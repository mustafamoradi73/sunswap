package org.tron.core.actuator;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.tron.common.utils.Commons;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.DelegatedResourceAccountIndexCapsule;
import org.tron.core.capsule.DelegatedResourceCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.VotesCapsule;
import org.tron.core.db.DelegationService;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.store.AccountStore;
import org.tron.core.store.DelegatedResourceAccountIndexStore;
import org.tron.core.store.DelegatedResourceStore;
import org.tron.core.store.DynamicPropertiesStore;
import org.tron.core.store.VotesStore;
import org.tron.protos.Protocol.Account.AccountResource;
import org.tron.protos.Protocol.Account.Frozen;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;
import org.tron.protos.Protocol.Transaction.Result.code;
import org.tron.protos.contract.BalanceContract.UnfreezeBalanceContract;

import static org.tron.core.Constant.SUN_TOKEN_ID;

@Slf4j(topic = "actuator")
public class UnfreezeBalanceActuator extends AbstractActuator {

  public UnfreezeBalanceActuator() {
    super(ContractType.UnfreezeBalanceContract, UnfreezeBalanceContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule)result;
    if (Objects.isNull(ret)){
      throw new RuntimeException("TransactionResultCapsule is null");
    }

    long fee = calcFee();
    final UnfreezeBalanceContract unfreezeBalanceContract;
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    DelegatedResourceStore delegatedResourceStore = chainBaseManager.getDelegatedResourceStore();
    DelegatedResourceAccountIndexStore delegatedResourceAccountIndexStore = chainBaseManager
        .getDelegatedResourceAccountIndexStore();
    VotesStore votesStore = chainBaseManager.getVotesStore();
    DelegationService delegationService = chainBaseManager.getDelegationService();
    try {
      unfreezeBalanceContract = any.unpack(UnfreezeBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    byte[] ownerAddress = unfreezeBalanceContract.getOwnerAddress().toByteArray();

    AccountCapsule accountCapsule = chainBaseManager.getAccountStore().get(ownerAddress);
    int chargingType = chainBaseManager.getDynamicPropertiesStore().getSideChainChargingType();
    long oldBalance;
    if (chargingType == 1) {
      oldBalance = accountCapsule.getAssetMapV2().getOrDefault(SUN_TOKEN_ID, 0L);
    }
    else {
      oldBalance = accountCapsule.getBalance();
    }

    //
//    delegationService.withdrawReward(ownerAddress);//3.7

    long unfreezeBalance = 0L;

    byte[] receiverAddress = unfreezeBalanceContract.getReceiverAddress().toByteArray();
    //If the receiver is not included in the contract, unfreeze frozen balance for this account.
    //otherwise,unfreeze delegated frozen balance provided this account.
    if (!ArrayUtils.isEmpty(receiverAddress) && dynamicStore.supportDR()) {
      byte[] key = DelegatedResourceCapsule
          .createDbKey(unfreezeBalanceContract.getOwnerAddress().toByteArray(),
              unfreezeBalanceContract.getReceiverAddress().toByteArray());
      DelegatedResourceCapsule delegatedResourceCapsule = delegatedResourceStore
          .get(key);

      switch (unfreezeBalanceContract.getResource()) {
        case BANDWIDTH:
          unfreezeBalance = delegatedResourceCapsule.getFrozenBalanceForBandwidth();
          delegatedResourceCapsule.setFrozenBalanceForBandwidth(0, 0);
          accountCapsule.addDelegatedFrozenBalanceForBandwidth(-unfreezeBalance);
          break;
        case ENERGY:
          unfreezeBalance = delegatedResourceCapsule.getFrozenBalanceForEnergy();
          delegatedResourceCapsule.setFrozenBalanceForEnergy(0, 0);
          accountCapsule.addDelegatedFrozenBalanceForEnergy(-unfreezeBalance);
          break;
        default:
          //this should never happen
          break;
      }

      AccountCapsule receiverCapsule = accountStore.get(receiverAddress);
      if (receiverCapsule != null && receiverCapsule.getType() != AccountType.Contract) {
        switch (unfreezeBalanceContract.getResource()) {
          case BANDWIDTH:
            if (dynamicStore.getAllowTvmSolidity059() == 1
                && receiverCapsule.getAcquiredDelegatedFrozenBalanceForBandwidth()
                < unfreezeBalance) {
              receiverCapsule.setAcquiredDelegatedFrozenBalanceForBandwidth(0);
            } else {
              receiverCapsule.addAcquiredDelegatedFrozenBalanceForBandwidth(-unfreezeBalance);
            }
            break;
          case ENERGY:
            if (dynamicStore.getAllowTvmSolidity059() == 1
                && receiverCapsule.getAcquiredDelegatedFrozenBalanceForEnergy() < unfreezeBalance) {
              receiverCapsule.setAcquiredDelegatedFrozenBalanceForEnergy(0);
            } else {
              receiverCapsule.addAcquiredDelegatedFrozenBalanceForEnergy(-unfreezeBalance);
            }
            break;
          default:
            //this should never happen
            break;
        }
        accountStore.put(receiverCapsule.createDbKey(), receiverCapsule);
      }

      if (chargingType == 1) {
        accountCapsule.setAssetAmountV2(SUN_TOKEN_ID.getBytes(), oldBalance + unfreezeBalance);
      }
      else {
        accountCapsule.setBalance(oldBalance + unfreezeBalance);
      }

      if (delegatedResourceCapsule.getFrozenBalanceForBandwidth() == 0
          && delegatedResourceCapsule.getFrozenBalanceForEnergy() == 0) {
        delegatedResourceStore.delete(key);

        //modify DelegatedResourceAccountIndexStore
        {
          DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = delegatedResourceAccountIndexStore
              .get(ownerAddress);
          if (delegatedResourceAccountIndexCapsule != null) {
            List<ByteString> toAccountsList = new ArrayList<>(delegatedResourceAccountIndexCapsule
                .getToAccountsList());
            toAccountsList.remove(ByteString.copyFrom(receiverAddress));
            delegatedResourceAccountIndexCapsule.setAllToAccounts(toAccountsList);
            delegatedResourceAccountIndexStore
                .put(ownerAddress, delegatedResourceAccountIndexCapsule);
          }
        }

        {
          DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = delegatedResourceAccountIndexStore
              .get(receiverAddress);
          if (delegatedResourceAccountIndexCapsule != null) {
            List<ByteString> fromAccountsList = new ArrayList<>(delegatedResourceAccountIndexCapsule
                .getFromAccountsList());
            fromAccountsList.remove(ByteString.copyFrom(ownerAddress));
            delegatedResourceAccountIndexCapsule.setAllFromAccounts(fromAccountsList);
            delegatedResourceAccountIndexStore
                .put(receiverAddress, delegatedResourceAccountIndexCapsule);
          }
        }

      } else {
        delegatedResourceStore.put(key, delegatedResourceCapsule);
      }
    } else {
      switch (unfreezeBalanceContract.getResource()) {
        case BANDWIDTH:

          List<Frozen> frozenList = Lists.newArrayList();
          frozenList.addAll(accountCapsule.getFrozenList());
          Iterator<Frozen> iterator = frozenList.iterator();
          long now = dynamicStore.getLatestBlockHeaderTimestamp();
          while (iterator.hasNext()) {
            Frozen next = iterator.next();
            if (next.getExpireTime() <= now) {
              unfreezeBalance += next.getFrozenBalance();
              iterator.remove();
            }
          }

          if (chargingType == 1) {
            accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
                    .clearFrozen().addAllFrozen(frozenList).build());
            accountCapsule.setAssetAmountV2(SUN_TOKEN_ID.getBytes(), oldBalance + unfreezeBalance);
          }
          else {
            accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
                    .setBalance(oldBalance + unfreezeBalance)
                    .clearFrozen().addAllFrozen(frozenList).build());
          }

          break;
        case ENERGY:
          unfreezeBalance = accountCapsule.getAccountResource().getFrozenBalanceForEnergy()
              .getFrozenBalance();

          AccountResource newAccountResource = accountCapsule.getAccountResource().toBuilder()
              .clearFrozenBalanceForEnergy().build();
          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
              .setBalance(oldBalance + unfreezeBalance)
              .setAccountResource(newAccountResource).build());

          if (chargingType == 1) {
            accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
//              .setBalance(oldBalance + unfreezeBalance)
                    .setAccountResource(newAccountResource).build());
            accountCapsule.setAssetAmountV2(SUN_TOKEN_ID.getBytes(), oldBalance + unfreezeBalance);
          }
          else {
            accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
                    .setBalance(oldBalance + unfreezeBalance)
                    .setAccountResource(newAccountResource).build());
          }

          break;
        default:
          //this should never happen
          break;
      }

    }

    switch (unfreezeBalanceContract.getResource()) {
      case BANDWIDTH:
        dynamicStore
            .addTotalNetWeight(-unfreezeBalance / 1_000_000L);
        break;
      case ENERGY:
        dynamicStore
            .addTotalEnergyWeight(-unfreezeBalance / 1_000_000L);
        break;
      default:
        //this should never happen
        break;
    }

    VotesCapsule votesCapsule;
    if (!votesStore.has(ownerAddress)) {
      votesCapsule = new VotesCapsule(unfreezeBalanceContract.getOwnerAddress(),
          accountCapsule.getVotesList());
    } else {
      votesCapsule = votesStore.get(ownerAddress);
    }
    accountCapsule.clearVotes();
    votesCapsule.clearNewVotes();

    accountStore.put(ownerAddress, accountCapsule);

    votesStore.put(ownerAddress, votesCapsule);

    ret.setUnfreezeAmount(unfreezeBalance);
    ret.setStatus(fee, code.SUCESS);

    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.any == null) {
      throw new ContractValidateException("No contract!");
    }
    if (chainBaseManager == null) {
      throw new ContractValidateException("No account store or dynamic store!");
    }
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    DelegatedResourceStore delegatedResourceStore = chainBaseManager.getDelegatedResourceStore();
    if (!this.any.is(UnfreezeBalanceContract.class)) {
      throw new ContractValidateException(
          "contract type error, expected type [UnfreezeBalanceContract], real type[" + any
              .getClass() + "]");
    }
    final UnfreezeBalanceContract unfreezeBalanceContract;
    try {
      unfreezeBalanceContract = this.any.unpack(UnfreezeBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = unfreezeBalanceContract.getOwnerAddress().toByteArray();
    if (!Commons.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
          "Account[" + readableOwnerAddress + "] does not exist");
    }
    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    byte[] receiverAddress = unfreezeBalanceContract.getReceiverAddress().toByteArray();
    //If the receiver is not included in the contract, unfreeze frozen balance for this account.
    //otherwise,unfreeze delegated frozen balance provided this account.
    if (!ArrayUtils.isEmpty(receiverAddress) && dynamicStore.supportDR()) {
      if (Arrays.equals(receiverAddress, ownerAddress)) {
        throw new ContractValidateException(
            "receiverAddress must not be the same as ownerAddress");
      }

      if (!Commons.addressValid(receiverAddress)) {
        throw new ContractValidateException("Invalid receiverAddress");
      }

      AccountCapsule receiverCapsule = accountStore.get(receiverAddress);
      if (receiverCapsule == null) {
        String readableReceiverAddress = StringUtil.createReadableString(receiverAddress);
        throw new ContractValidateException(
            "Receiver Account[" + readableReceiverAddress + "] does not exist");
      }

      byte[] key = DelegatedResourceCapsule
          .createDbKey(unfreezeBalanceContract.getOwnerAddress().toByteArray(),
              unfreezeBalanceContract.getReceiverAddress().toByteArray());
      DelegatedResourceCapsule delegatedResourceCapsule = delegatedResourceStore
          .get(key);
      if (delegatedResourceCapsule == null) {
        throw new ContractValidateException(
            "delegated Resource does not exist");
      }

      switch (unfreezeBalanceContract.getResource()) {
        case BANDWIDTH:
          if (delegatedResourceCapsule.getFrozenBalanceForBandwidth() <= 0) {
            throw new ContractValidateException("no delegatedFrozenBalance(BANDWIDTH)");
          }

          if (dynamicStore.getAllowTvmSolidity059() != 1
                && receiverCapsule != null
                && receiverCapsule.getType() != AccountType.Contract
                && receiverCapsule.getAcquiredDelegatedFrozenBalanceForBandwidth()
                < delegatedResourceCapsule.getFrozenBalanceForBandwidth()) {
          throw new ContractValidateException(
                  "AcquiredDelegatedFrozenBalanceForBandwidth[" + receiverCapsule
                          .getAcquiredDelegatedFrozenBalanceForBandwidth() + "] < delegatedBandwidth["
                          + delegatedResourceCapsule.getFrozenBalanceForBandwidth()
                          + "]");
          }

          if (delegatedResourceCapsule.getExpireTimeForBandwidth() > now) {
            throw new ContractValidateException("It's not time to unfreeze.");
          }
          break;
        case ENERGY:
          if (delegatedResourceCapsule.getFrozenBalanceForEnergy() <= 0) {
            throw new ContractValidateException("no delegateFrozenBalance(Energy)");
          }
          if (dynamicStore.getAllowTvmSolidity059() != 1
              && receiverCapsule != null
              && receiverCapsule.getType() != AccountType.Contract
              && receiverCapsule.getAcquiredDelegatedFrozenBalanceForEnergy()
              < delegatedResourceCapsule.getFrozenBalanceForEnergy()) {
            throw new ContractValidateException(
                "AcquiredDelegatedFrozenBalanceForEnergy[" + receiverCapsule
                    .getAcquiredDelegatedFrozenBalanceForEnergy() + "] < delegatedEnergy["
                    + delegatedResourceCapsule.getFrozenBalanceForEnergy() +
                    "]");
          }

          if (delegatedResourceCapsule.getExpireTimeForEnergy(dynamicStore) > now) {
            throw new ContractValidateException("It's not time to unfreeze.");
          }
          break;
        default:
          throw new ContractValidateException(
              "ResourceCode error.valid ResourceCode[BANDWIDTH、Energy]");
      }

    } else {
      switch (unfreezeBalanceContract.getResource()) {
        case BANDWIDTH:
          if (accountCapsule.getFrozenCount() <= 0) {
            throw new ContractValidateException("no frozenBalance(BANDWIDTH)");
          }

          long allowedUnfreezeCount = accountCapsule.getFrozenList().stream()
              .filter(frozen -> frozen.getExpireTime() <= now).count();
          if (allowedUnfreezeCount <= 0) {
            throw new ContractValidateException("It's not time to unfreeze(BANDWIDTH).");
          }
          break;
        case ENERGY:
          Frozen frozenBalanceForEnergy = accountCapsule.getAccountResource()
              .getFrozenBalanceForEnergy();
          if (frozenBalanceForEnergy.getFrozenBalance() <= 0) {
            throw new ContractValidateException("no frozenBalance(Energy)");
          }
          if (frozenBalanceForEnergy.getExpireTime() > now) {
            throw new ContractValidateException("It's not time to unfreeze(Energy).");
          }

          break;
        default:
          throw new ContractValidateException(
              "ResourceCode error.valid ResourceCode[BANDWIDTH、Energy]");
      }

    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(UnfreezeBalanceContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
