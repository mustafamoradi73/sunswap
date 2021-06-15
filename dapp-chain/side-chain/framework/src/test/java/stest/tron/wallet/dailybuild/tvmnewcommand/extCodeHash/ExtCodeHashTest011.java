package stest.tron.wallet.dailybuild.tvmnewcommand.extCodeHash;

import static org.hamcrest.core.StringContains.containsString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.spongycastle.util.encoders.Hex;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.tron.api.GrpcAPI.AccountResourceMessage;
import org.tron.api.WalletGrpc;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Hash;
import org.tron.common.utils.Utils;
import org.tron.core.Wallet;
import org.tron.protos.Protocol.TransactionInfo;
import org.tron.protos.contract.SmartContractOuterClass.SmartContract;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.Parameter.CommonConstant;
import stest.tron.wallet.common.client.utils.Base58;
import stest.tron.wallet.common.client.utils.PublicMethedForDailybuild;

@Slf4j
public class ExtCodeHashTest011 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethedForDailybuild.getFinalAddress(testKey002);

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

  private byte[] extCodeHashContractAddress = null;
  private byte[] testContractAddress = null;

  private String expectedCodeHash = null;

  private ECKey ecKey1 = new ECKey(Utils.getRandom());
  private byte[] dev001Address = ecKey1.getAddress();
  private String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

  private ECKey ecKey2 = new ECKey(Utils.getRandom());
  private byte[] user001Address = ecKey2.getAddress();
  private String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() {

    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    PublicMethedForDailybuild.printAddress(dev001Key);
    PublicMethedForDailybuild.printAddress(user001Key);
  }

  @Test(enabled = true, description = "Deploy extcodehash contract")
  public void test01DeployExtCodeHashContract() {
    Assert.assertTrue(PublicMethedForDailybuild.sendcoin(dev001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethedForDailybuild.sendcoin(user001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));

    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);

    //before deploy, check account resource
    AccountResourceMessage accountResource = PublicMethedForDailybuild
        .getAccountResource(dev001Address,
            blockingStubFull);
    long energyLimit = accountResource.getEnergyLimit();
    long energyUsage = accountResource.getEnergyUsed();
    long balanceBefore = PublicMethedForDailybuild.queryAccount(dev001Key, blockingStubFull)
        .getBalance();
    logger.info("before energyLimit is " + Long.toString(energyLimit));
    logger.info("before energyUsage is " + Long.toString(energyUsage));
    logger.info("before balanceBefore is " + Long.toString(balanceBefore));

    String filePath = "./src/test/resources/soliditycode/extCodeHash11.sol";
    String contractName = "Counter";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    expectedCodeHash = ByteArray.toHexString(Hash.sha3(Hex.decode(code)));
    logger.info("expectedCodeHash: " + expectedCodeHash);

    String transferTokenTxid = PublicMethedForDailybuild
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "",
            maxFeeLimit, 0L, 0, 10000,
            "0", 0, null, dev001Key,
            dev001Address, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethedForDailybuild
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);
    extCodeHashContractAddress = infoById.get().getContractAddress().toByteArray();

    accountResource = PublicMethedForDailybuild.getAccountResource(dev001Address, blockingStubFull);
    energyLimit = accountResource.getEnergyLimit();
    energyUsage = accountResource.getEnergyUsed();
    long balanceAfter = PublicMethedForDailybuild.queryAccount(dev001Key, blockingStubFull)
        .getBalance();

    logger.info("after energyLimit is " + Long.toString(energyLimit));
    logger.info("after energyUsage is " + Long.toString(energyUsage));
    logger.info("after balanceAfter is " + Long.toString(balanceAfter));
    transferTokenTxid = PublicMethedForDailybuild.triggerContract(extCodeHashContractAddress,
        "getCodeHashByAddr()", "#", false, 0,
        1000000000L, "0", 0, user001Address, user001Key,
        blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);

    Assert.assertTrue(infoById.get().getResultValue() != 0);
    Assert
        .assertThat(ByteArray
                .toStr(infoById.get().getResMessage().toByteArray()),
            containsString("REVERT opcode executed"));
  }


  @Test(enabled = true, description = "Deploy extcodehash contract")
  public void test01DeployExtCodeHashContract1() {
    Assert.assertTrue(PublicMethedForDailybuild.sendcoin(dev001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethedForDailybuild.sendcoin(user001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));

    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);

    //before deploy, check account resource
    AccountResourceMessage accountResource = PublicMethedForDailybuild
        .getAccountResource(dev001Address,
            blockingStubFull);
    long energyLimit = accountResource.getEnergyLimit();
    long energyUsage = accountResource.getEnergyUsed();
    long balanceBefore = PublicMethedForDailybuild.queryAccount(dev001Key, blockingStubFull)
        .getBalance();
    logger.info("before energyLimit is " + Long.toString(energyLimit));
    logger.info("before energyUsage is " + Long.toString(energyUsage));
    logger.info("before balanceBefore is " + Long.toString(balanceBefore));

    String filePath = "./src/test/resources/soliditycode/extCodeHash11.sol";
    String contractName = "Counter1";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    expectedCodeHash = ByteArray.toHexString(Hash.sha3(Hex.decode(code)));
    logger.info("expectedCodeHash: " + expectedCodeHash);

    String transferTokenTxid = PublicMethedForDailybuild
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "",
            maxFeeLimit, 0L, 0, 10000,
            "0", 0, null, dev001Key,
            dev001Address, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethedForDailybuild
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);
    extCodeHashContractAddress = infoById.get().getContractAddress().toByteArray();

    accountResource = PublicMethedForDailybuild.getAccountResource(dev001Address, blockingStubFull);
    energyLimit = accountResource.getEnergyLimit();
    energyUsage = accountResource.getEnergyUsed();
    long balanceAfter = PublicMethedForDailybuild.queryAccount(dev001Key, blockingStubFull)
        .getBalance();

    logger.info("after energyLimit is " + Long.toString(energyLimit));
    logger.info("after energyUsage is " + Long.toString(energyUsage));
    logger.info("after balanceAfter is " + Long.toString(balanceAfter));
    transferTokenTxid = PublicMethedForDailybuild.triggerContract(extCodeHashContractAddress,
        "getCodeHashByAddr()", "#", false, 0,
        1000000000L, "0", 0, user001Address, user001Key,
        blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethedForDailybuild
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);

    TransactionInfo transactionInfo = infoById.get();
    List<String> retList = PublicMethedForDailybuild
        .getStrings(transactionInfo.getContractResult(0).toByteArray());

    logger.info(
        "the value: " + retList);

    Assert.assertEquals(retList.get(1),
        retList.get(0));
    logger.info("EnergyUsageTotal: " + transactionInfo.getReceipt().getEnergyUsageTotal());
    logger.info("NetUsage: " + transactionInfo.getReceipt().getNetUsage());

    extCodeHashContractAddress = infoById.get().getContractAddress().toByteArray();
    SmartContract smartContract = PublicMethedForDailybuild.getContract(extCodeHashContractAddress,
        blockingStubFull);
    Assert.assertNotNull(smartContract.getAbi());
  }


  @Test(enabled = true, description = "Deploy extcodehash contract")
  public void test01DeployExtCodeHashContract2() {
    Assert.assertTrue(PublicMethedForDailybuild.sendcoin(dev001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethedForDailybuild.sendcoin(user001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));

    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);

    //before deploy, check account resource
    AccountResourceMessage accountResource = PublicMethedForDailybuild
        .getAccountResource(dev001Address,
            blockingStubFull);
    long energyLimit = accountResource.getEnergyLimit();
    long energyUsage = accountResource.getEnergyUsed();
    long balanceBefore = PublicMethedForDailybuild.queryAccount(dev001Key, blockingStubFull)
        .getBalance();
    logger.info("before energyLimit is " + Long.toString(energyLimit));
    logger.info("before energyUsage is " + Long.toString(energyUsage));
    logger.info("before balanceBefore is " + Long.toString(balanceBefore));

    String filePath = "./src/test/resources/soliditycode/extCodeHash11.sol";
    String contractName = "Counter2";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    expectedCodeHash = ByteArray.toHexString(Hash.sha3(Hex.decode(code)));
    logger.info("expectedCodeHash: " + expectedCodeHash);

    String transferTokenTxid = PublicMethedForDailybuild
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "",
            maxFeeLimit, 0L, 0, 10000,
            "0", 0, null, dev001Key,
            dev001Address, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethedForDailybuild
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);
    extCodeHashContractAddress = infoById.get().getContractAddress().toByteArray();

    accountResource = PublicMethedForDailybuild.getAccountResource(dev001Address, blockingStubFull);
    energyLimit = accountResource.getEnergyLimit();
    energyUsage = accountResource.getEnergyUsed();
    long balanceAfter = PublicMethedForDailybuild.queryAccount(dev001Key, blockingStubFull)
        .getBalance();

    logger.info("after energyLimit is " + Long.toString(energyLimit));
    logger.info("after energyUsage is " + Long.toString(energyUsage));
    logger.info("after balanceAfter is " + Long.toString(balanceAfter));

    String num = "\"" + Base58.encode58Check(dev001Address) + "\"";

    transferTokenTxid = PublicMethedForDailybuild.triggerContract(extCodeHashContractAddress,
        "getCodeHashByAddr(address)", num, false, 0,
        1000000000L, "0", 0, user001Address, user001Key,
        blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);

    TransactionInfo transactionInfo = infoById.get();
    List<String> retList = PublicMethedForDailybuild
        .getStrings(transactionInfo.getContractResult(0).toByteArray());

    logger.info(
        "the value: " + retList);
    Assert.assertEquals(retList.get(1),
        retList.get(0));
  }


  @Test(enabled = true, description = "Deploy extcodehash contract")
  public void test01DeployExtCodeHashContract3() {
    Assert.assertTrue(PublicMethedForDailybuild.sendcoin(dev001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethedForDailybuild.sendcoin(user001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));

    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);

    //before deploy, check account resource
    AccountResourceMessage accountResource = PublicMethedForDailybuild
        .getAccountResource(dev001Address,
            blockingStubFull);
    long energyLimit = accountResource.getEnergyLimit();
    long energyUsage = accountResource.getEnergyUsed();
    long balanceBefore = PublicMethedForDailybuild.queryAccount(dev001Key, blockingStubFull)
        .getBalance();
    logger.info("before energyLimit is " + Long.toString(energyLimit));
    logger.info("before energyUsage is " + Long.toString(energyUsage));
    logger.info("before balanceBefore is " + Long.toString(balanceBefore));

    String filePath = "./src/test/resources/soliditycode/extCodeHash11.sol";
    String contractName = "Counter2";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    expectedCodeHash = ByteArray.toHexString(Hash.sha3(Hex.decode(code)));
    logger.info("expectedCodeHash: " + expectedCodeHash);

    String transferTokenTxid = PublicMethedForDailybuild
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "",
            maxFeeLimit, 0L, 0, 10000,
            "0", 0, null, dev001Key,
            dev001Address, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethedForDailybuild
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);
    extCodeHashContractAddress = infoById.get().getContractAddress().toByteArray();

    accountResource = PublicMethedForDailybuild.getAccountResource(dev001Address, blockingStubFull);
    energyLimit = accountResource.getEnergyLimit();
    energyUsage = accountResource.getEnergyUsed();
    long balanceAfter = PublicMethedForDailybuild.queryAccount(dev001Key, blockingStubFull)
        .getBalance();

    logger.info("after energyLimit is " + Long.toString(energyLimit));
    logger.info("after energyUsage is " + Long.toString(energyUsage));
    logger.info("after balanceAfter is " + Long.toString(balanceAfter));

    String num = "\"" + Base58.encode58Check(dev001Address) + "\"";

    transferTokenTxid = PublicMethedForDailybuild.triggerContract(extCodeHashContractAddress,
        "getCodeHashByAddr(address)", num, false, 0,
        1000000000L, "0", 0, user001Address, user001Key,
        blockingStubFull);

    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);
    infoById = PublicMethedForDailybuild
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);

    TransactionInfo transactionInfo = infoById.get();
    List<String> retList = PublicMethedForDailybuild
        .getStrings(transactionInfo.getContractResult(0).toByteArray());

    logger.info(
        "the value: " + retList);
    Assert.assertEquals(retList.get(1),
        retList.get(0));
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


