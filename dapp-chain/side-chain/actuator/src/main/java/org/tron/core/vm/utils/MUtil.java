package org.tron.core.vm.utils;


import static org.tron.common.utils.DecodeUtil.addressPreFixByte;

import org.tron.common.utils.Base58;
import org.tron.common.utils.Commons;
import org.tron.common.utils.DBConfig;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.actuator.TransferAssetActuator;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.vm.VMUtils;
import org.tron.core.vm.repository.Repository;
import org.tron.protos.Protocol;

public class MUtil {

  private MUtil() {
  }

  public static void transfer(Repository deposit, byte[] fromAddress, byte[] toAddress, long amount)
      throws ContractValidateException, ContractExeException {
    if (0 == amount) {
      return;
    }
    VMUtils.validateForSmartContract(deposit, fromAddress, toAddress, amount);
    VMUtils.executeForSmartContract(deposit, fromAddress, toAddress, amount);
  }

  public static void transferAsset(Repository deposit, byte[] fromAddress, byte[] toAddress, byte[] tokenId, long amount)
          throws ContractValidateException, ContractExeException {
    if (0 == amount) {
      return;
    }

    VMUtils.validateForSmartContract(deposit, fromAddress, toAddress, tokenId, amount);
    VMUtils.executeForSmartContract(deposit, fromAddress, toAddress, tokenId, amount);
  }

  public static void transferAllToken(Repository deposit, byte[] fromAddress, byte[] toAddress) {
    AccountCapsule fromAccountCap = deposit.getAccount(fromAddress);
    Protocol.Account.Builder fromBuilder = fromAccountCap.getInstance().toBuilder();
    AccountCapsule toAccountCap = deposit.getAccount(toAddress);
    Protocol.Account.Builder toBuilder = toAccountCap.getInstance().toBuilder();
    fromAccountCap.getAssetMapV2().forEach((tokenId, amount) -> {
      toBuilder.putAssetV2(tokenId, toBuilder.getAssetV2Map().getOrDefault(tokenId, 0L) + amount);
      fromBuilder.putAssetV2(tokenId, 0L);
    });
    deposit.putAccountValue(fromAddress, new AccountCapsule(fromBuilder.build()));
    deposit.putAccountValue(toAddress, new AccountCapsule(toBuilder.build()));
  }

  public static void transferToken(Repository deposit, byte[] fromAddress, byte[] toAddress,
      String tokenId, long amount)
      throws ContractValidateException {
    if (0 == amount) {
      return;
    }
    VMUtils.validateForSmartContract(deposit, fromAddress, toAddress, tokenId.getBytes(), amount);
    deposit.addTokenBalance(toAddress, tokenId.getBytes(), amount);
    deposit.addTokenBalance(fromAddress, tokenId.getBytes(), -amount);
  }

  public static byte[] convertToTronAddress(byte[] address) {
    if (address.length == 20) {
      byte[] newAddress = new byte[21];
      byte[] temp = new byte[]{addressPreFixByte};
      System.arraycopy(temp, 0, newAddress, 0, temp.length);
      System.arraycopy(address, 0, newAddress, temp.length, address.length);
      address = newAddress;
    }
    return address;
  }

  public static String encode58Check(byte[] input) {
    byte[] hash0 = Sha256Hash.hash(DBConfig.isECKeyCryptoEngine(), input);
    byte[] hash1 = Sha256Hash.hash(DBConfig.isECKeyCryptoEngine(), hash0);
    byte[] inputCheck = new byte[input.length + 4];
    System.arraycopy(input, 0, inputCheck, 0, input.length);
    System.arraycopy(hash1, 0, inputCheck, input.length, 4);
    return Base58.encode(inputCheck);
  }

  public static boolean isNullOrEmpty(String str) {
    return (str == null) || str.isEmpty();
  }


  public static boolean isNotNullOrEmpty(String str) {
    return !isNullOrEmpty(str);
  }

  public static byte[] allZero32TronAddress() {
    byte[] newAddress = new byte[32];
    byte[] temp = new byte[]{addressPreFixByte};
    System.arraycopy(temp, 0, newAddress, 11, temp.length);

    return newAddress;
  }

}
