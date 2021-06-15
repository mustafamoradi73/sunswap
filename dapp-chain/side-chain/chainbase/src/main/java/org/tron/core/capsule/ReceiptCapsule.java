package org.tron.core.capsule;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.tron.common.utils.Commons;
import org.tron.common.utils.DBConfig;
import org.tron.common.utils.ForkUtils;
import org.tron.common.utils.Sha256Hash;
import org.tron.common.utils.StringUtil;
import org.tron.core.Constant;
import org.tron.core.config.args.Parameter.ForkBlockVersionEnum;
import org.tron.core.db.EnergyProcessor;
import org.tron.core.exception.BalanceInsufficientException;
import org.tron.core.store.AccountStore;
import org.tron.core.store.DynamicPropertiesStore;
import org.tron.protos.Protocol.ResourceReceipt;
import org.tron.protos.Protocol.Transaction.Result.contractResult;

import static org.tron.core.Constant.SUN_TOKEN_ID;

public class ReceiptCapsule {

  private ResourceReceipt receipt;
  @Getter
  @Setter
  private long multiSignFee;

  private Sha256Hash receiptAddress;

  public ReceiptCapsule(ResourceReceipt data, Sha256Hash receiptAddress) {
    this.receipt = data;
    this.receiptAddress = receiptAddress;
  }

  public ReceiptCapsule(Sha256Hash receiptAddress) {
    this.receipt = ResourceReceipt.newBuilder().build();
    this.receiptAddress = receiptAddress;
  }

  public static ResourceReceipt copyReceipt(ReceiptCapsule origin) {
    return origin.getReceipt().toBuilder().build();
  }

  private static boolean checkForEnergyLimit(DynamicPropertiesStore ds) {
    long blockNum = ds.getLatestBlockHeaderNumber();
    return blockNum >= DBConfig.getBlockNumForEneryLimit();
  }

  public ResourceReceipt getReceipt() {
    return this.receipt;
  }

  public void setReceipt(ResourceReceipt receipt) {
    this.receipt = receipt;
  }

  public Sha256Hash getReceiptAddress() {
    return this.receiptAddress;
  }

  public void addNetFee(long netFee) {
    this.receipt = this.receipt.toBuilder().setNetFee(getNetFee() + netFee).build();
  }

  public long getEnergyUsage() {
    return this.receipt.getEnergyUsage();
  }

  public void setEnergyUsage(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setEnergyUsage(energyUsage).build();
  }

  public long getEnergyFee() {
    return this.receipt.getEnergyFee();
  }

  public void setEnergyFee(long energyFee) {
    this.receipt = this.receipt.toBuilder().setEnergyFee(energyFee).build();
  }

  public long getOriginEnergyUsage() {
    return this.receipt.getOriginEnergyUsage();
  }

  public void setOriginEnergyUsage(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setOriginEnergyUsage(energyUsage).build();
  }

  public long getEnergyUsageTotal() {
    return this.receipt.getEnergyUsageTotal();
  }

  public void setEnergyUsageTotal(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setEnergyUsageTotal(energyUsage).build();
  }

  public long getNetUsage() {
    return this.receipt.getNetUsage();
  }

  public void setNetUsage(long netUsage) {
    this.receipt = this.receipt.toBuilder().setNetUsage(netUsage).build();
  }

  public long getNetFee() {
    return this.receipt.getNetFee();
  }

  public void setNetFee(long netFee) {
    this.receipt = this.receipt.toBuilder().setNetFee(netFee).build();
  }

  /**
   * payEnergyBill pay receipt energy bill by energy processor.
   */
  public void payEnergyBill(DynamicPropertiesStore dynamicPropertiesStore,
      AccountStore accountStore, ForkUtils forkUtils, AccountCapsule origin, AccountCapsule caller,
      long percent, long originEnergyLimit, EnergyProcessor energyProcessor, long now)
      throws BalanceInsufficientException {
    if (receipt.getEnergyUsageTotal() <= 0) {
      return;
    }

    if (Objects.isNull(origin)) {
      payEnergyBill(dynamicPropertiesStore, accountStore, forkUtils, caller,
          receipt.getEnergyUsageTotal(), energyProcessor, now);
      return;
    }

    if (caller.getAddress().equals(origin.getAddress())) {
      payEnergyBill(dynamicPropertiesStore, accountStore, forkUtils, caller,
          receipt.getEnergyUsageTotal(), energyProcessor, now);
    } else {
      long originUsage = Math.multiplyExact(receipt.getEnergyUsageTotal(), percent) / 100;
      originUsage = getOriginUsage(dynamicPropertiesStore, origin, originEnergyLimit,
          energyProcessor,
          originUsage);

      long callerUsage = receipt.getEnergyUsageTotal() - originUsage;
      energyProcessor.useEnergy(origin, originUsage, now);
      this.setOriginEnergyUsage(originUsage);
      payEnergyBill(dynamicPropertiesStore, accountStore, forkUtils,
          caller, callerUsage, energyProcessor, now);
    }
  }

  private long getOriginUsage(DynamicPropertiesStore dynamicPropertiesStore, AccountCapsule origin,
      long originEnergyLimit,
      EnergyProcessor energyProcessor, long originUsage) {

    return Math.min(originUsage,
            Math.min(energyProcessor.getAccountLeftEnergyFromFreeze(origin), originEnergyLimit));
  }

  private void payEnergyBill(
      DynamicPropertiesStore dynamicPropertiesStore, AccountStore accountStore, ForkUtils forkUtils,
      AccountCapsule account,
      long usage,
      EnergyProcessor energyProcessor,
      long now) throws BalanceInsufficientException {
    long accountEnergyLeft = energyProcessor.getAccountLeftEnergyFromFreeze(account);
    if (accountEnergyLeft >= usage) {
      energyProcessor.useEnergy(account, usage, now);
      this.setEnergyUsage(usage);
    } else {
      energyProcessor.useEnergy(account, accountEnergyLeft, now);

      if (dynamicPropertiesStore.getAllowAdaptiveEnergy() == 1) {
        long blockEnergyUsage =
                dynamicPropertiesStore.getBlockEnergyUsage() + (usage - accountEnergyLeft);
        dynamicPropertiesStore.saveBlockEnergyUsage(blockEnergyUsage);
      }

      long energyFee;
      int chargingType = dynamicPropertiesStore.getSideChainChargingType();
      long dynamicEnergyFee = dynamicPropertiesStore.getEnergyFee(chargingType);

      if(chargingType == 0) {
        long sunPerEnergy = Constant.SUN_PER_ENERGY;
        if (dynamicEnergyFee > 0) {
          sunPerEnergy = dynamicEnergyFee;
        }
        energyFee = (usage - accountEnergyLeft) * sunPerEnergy;
        this.setEnergyUsage(accountEnergyLeft);
        this.setEnergyFee(energyFee);

        long balance = account.getBalance();
        if (balance < energyFee) {
          throw new BalanceInsufficientException(
                  StringUtil.createReadableString(account.createDbKey()) + " insufficient balance");
        }
      } else {
        long sunPerEnergy = Constant.MICRO_SUN_TOKEN_PER_ENERGY;
        if (dynamicEnergyFee > 0) {
          sunPerEnergy = dynamicEnergyFee;
        }
        energyFee =
                (usage - accountEnergyLeft) * sunPerEnergy;
        this.setEnergyUsage(accountEnergyLeft);
        this.setEnergyFee(energyFee);
        long balance = account.getAssetMapV2().getOrDefault(SUN_TOKEN_ID, 0L);
        if (balance < energyFee) {
          throw new BalanceInsufficientException(
                  StringUtil.createReadableString(account.createDbKey()) + " insufficient token");
        }
      }
      Commons.adjustBalance(accountStore, account, -energyFee, chargingType);

      //send to blackHole
      Commons.adjustFund(dynamicPropertiesStore, energyFee);
    }

    accountStore.put(account.getAddress().toByteArray(), account);
  }

  public contractResult getResult() {
    return this.receipt.getResult();
  }

  public void setResult(contractResult success) {
    this.receipt = receipt.toBuilder().setResult(success).build();
  }
}
