package org.tron.core.actuator;

import static org.tron.core.Constant.SUN_TOKEN_ID;
import static org.tron.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;

import com.google.common.math.LongMath;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Arrays;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.Commons;
import org.tron.common.utils.DBConfig;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.db.DelegationService;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.store.AccountStore;
import org.tron.core.store.DynamicPropertiesStore;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;
import org.tron.protos.Protocol.Transaction.Result.code;
import org.tron.protos.contract.BalanceContract.WithdrawBalanceContract;

@Slf4j(topic = "actuator")
public class WithdrawBalanceActuator extends AbstractActuator {

  public WithdrawBalanceActuator() {
    super(ContractType.WithdrawBalanceContract, WithdrawBalanceContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule)result;
    if (Objects.isNull(ret)){
      throw new RuntimeException("TransactionResultCapsule is null");
    }

    long fee = calcFee();
    final WithdrawBalanceContract withdrawBalanceContract;
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    DelegationService delegationService = chainBaseManager.getDelegationService();
    try {
      withdrawBalanceContract = any.unpack(WithdrawBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

//    delegationService.withdrawReward(withdrawBalanceContract.getOwnerAddress()
//        .toByteArray());

    AccountCapsule accountCapsule = accountStore.
        get(withdrawBalanceContract.getOwnerAddress().toByteArray());
    int chargingType = chainBaseManager.getDynamicPropertiesStore().getSideChainChargingType();
    long oldBalance = accountCapsule.getBalance();
    long allowance = accountCapsule.getAllowance();

    long now = dynamicStore.getLatestBlockHeaderTimestamp();

    accountCapsule.getInstance().toBuilder();
    if (chargingType == 0) {
      accountCapsule.setBalance(oldBalance + allowance);
    } else {
      accountCapsule.addAssetAmountV2(SUN_TOKEN_ID.getBytes(), allowance);
    }
    accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
        .setAllowance(0L)
        .setLatestWithdrawTime(now)
        .build());
    accountStore.put(accountCapsule.createDbKey(), accountCapsule);
    ret.setWithdrawAmount(allowance);
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
    DelegationService delegationService = chainBaseManager.getDelegationService();
    if (!this.any.is(WithdrawBalanceContract.class)) {
      throw new ContractValidateException(
          "contract type error, expected type [WithdrawBalanceContract], real type[" + any
              .getClass() + "]");
    }
    final WithdrawBalanceContract withdrawBalanceContract;
    try {
      withdrawBalanceContract = this.any.unpack(WithdrawBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = withdrawBalanceContract.getOwnerAddress().toByteArray();
    if (!Commons.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] not exists");
    }

    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
    if (!chainBaseManager.getWitnessStore().has(ownerAddress)) {
      throw new ContractValidateException(
              ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] is not a witnessAccount");
    }
    //  allow withdraw allowance for GR on side-chain
/*    boolean isGP = DBConfig.getGenesisBlock().getWitnesses().stream().anyMatch(witness ->
        Arrays.equals(ownerAddress, witness.getAddress()));
    if (isGP) {
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + readableOwnerAddress
              + "] is a guard representative and is not allowed to withdraw Balance");
    }*/

    long latestWithdrawTime = accountCapsule.getLatestWithdrawTime();
    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    long witnessAllowanceFrozenTime = dynamicStore.getWitnessAllowanceFrozenTime() * 86_400_000L;

    if (now - latestWithdrawTime < witnessAllowanceFrozenTime) {
      throw new ContractValidateException("The last withdraw time is "
          + latestWithdrawTime + ", less than 24 hours");
    }

    /*if (accountCapsule.getAllowance() <= 0 &&
        delegationService.queryReward(ownerAddress) <= 0) {
      throw new ContractValidateException("witnessAccount does not have any reward");
    }*/
    if (accountCapsule.getAllowance() <= 0 && delegationService.queryReward(ownerAddress) <= 0) {
      throw new ContractValidateException("witnessAccount does not have any allowance");
    }
    try {
      long balance = accountCapsule.getBalanceByChargeType(chainBaseManager);
      LongMath.checkedAdd(balance, accountCapsule.getAllowance());
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(WithdrawBalanceContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
