package stest.tron.wallet.dailybuild.tvmnewcommand.transferfailed;

import static org.tron.protos.Protocol.TransactionInfo.code.FAILED;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.tron.api.GrpcAPI.AccountResourceMessage;
import org.tron.api.WalletGrpc;
import org.tron.api.WalletSolidityGrpc;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Utils;
import org.tron.core.Wallet;
import org.tron.core.vm.EnergyCost;
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.Transaction.Result.contractResult;
import org.tron.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.Parameter.CommonConstant;
import stest.tron.wallet.common.client.WalletClient;
import stest.tron.wallet.common.client.utils.Base58;
import stest.tron.wallet.common.client.utils.PublicMethedForDailybuild;

@Slf4j
public class TransferFailed001 {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] testNetAccountAddress = PublicMethedForDailybuild
      .getFinalAddress(testNetAccountKey);
  byte[] contractAddress = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contractExcAddress = ecKey1.getAddress();
  String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private ManagedChannel channelSolidity = null;
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);
  private final String tokenOwnerKey = Configuration.getByPath("testng.conf")
      .getString("tokenFoundationAccount.slideTokenOwnerKey");
  private final byte[] tokenOnwerAddress = PublicMethedForDailybuild.getFinalAddress(tokenOwnerKey);
  private final String tokenId = Configuration.getByPath("testng.conf")
      .getString("tokenFoundationAccount.slideTokenId");

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
    PublicMethedForDailybuild.printAddress(contractExcKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
  }

  @Test(enabled = true, description = "Transfer trx insufficient balance")
  public void test001TransferTrxInsufficientBalance() {
    Assert.assertTrue(PublicMethedForDailybuild
        .sendcoin(contractExcAddress, 10000000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    String filePath = "src/test/resources/soliditycode/TransferFailed001.sol";
    String contractName = "EnergyOfTransferFailedTest";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    contractAddress = PublicMethedForDailybuild
        .deployContract(contractName, abi, code, "", maxFeeLimit,
            2000000L, 100, null, contractExcKey,
            contractExcAddress, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);

    Account info;

    AccountResourceMessage resourceInfo = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    String txid = "";
    String num = "2000001";
    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTrxInsufficientBalance(uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long energyUsed = infoById.get().getReceipt().getEnergyUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    long energyUsageTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal);

    Account infoafter = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEnergyUsed:" + afterEnergyUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("infoById:" + infoById);
    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertEquals(contractResult.REVERT, infoById.get().getReceipt().getResult());
    Assert.assertEquals(
        "REVERT opcode executed",
        ByteArray.toStr(infoById.get().getResMessage().toByteArray()));
    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeEnergyUsed + energyUsed >= afterEnergyUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertNotEquals(10000000, energyUsageTotal);


  }


  @Test(enabled = true, description = "Transfer balance enough")
  public void test002TransferEnough() {

    //Assert.assertTrue(PublicMethedForDailybuild
    //    .sendcoin(contractAddress, 3000000L, testNetAccountAddress, testNetAccountKey,
    //        blockingStubFull));
    //PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Account info;

    AccountResourceMessage resourceInfo = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    String txid = "";
    String num = "1";
    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTrxInsufficientBalance(uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long energyUsed = infoById.get().getReceipt().getEnergyUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    long energyUsageTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal);

    Account infoafter = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEnergyUsed:" + afterEnergyUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    logger.info("infoById" + infoById);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance + fee - 1 == beforeBalance);
    Assert.assertTrue(beforeEnergyUsed + energyUsed >= afterEnergyUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);


  }


  @Test(enabled = true, description = "Transfer trx nonexistent target")
  public void test003TransferTrxNonexistentTarget() {

    //Assert.assertTrue(PublicMethedForDailybuild
    //    .sendcoin(contractAddress, 1000000L, testNetAccountAddress, testNetAccountKey,
    //        blockingStubFull));

    Account info;

    AccountResourceMessage resourceInfo = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    ECKey ecKey2 = new ECKey(Utils.getRandom());
    byte[] nonexistentAddress = ecKey2.getAddress();
    String txid = "";
    String num = "1" + ",\"" + Base58.encode58Check(nonexistentAddress) + "\"";

    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTrxNonexistentTarget(uint256,address)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

    Long fee = infoById.get().getFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long energyUsed = infoById.get().getReceipt().getEnergyUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    long energyUsageTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal);

    Account infoafter = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEnergyUsed:" + afterEnergyUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Account nonexistentAddressAccount = PublicMethedForDailybuild
        .queryAccount(nonexistentAddress, blockingStubFull1);
    Assert.assertEquals(1, nonexistentAddressAccount.getBalance());
    Assert.assertEquals(0, infoById.get().getResultValue());

    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeEnergyUsed + energyUsed >= afterEnergyUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertNotEquals(10000000, energyUsageTotal);

    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTrxNonexistentTarget(uint256,address)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

    fee = infoById.get().getFee();
    netUsed = infoById.get().getReceipt().getNetUsage();
    energyUsed = infoById.get().getReceipt().getEnergyUsage();
    netFee = infoById.get().getReceipt().getNetFee();
    long energyUsageTotal2 = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal2);

    infoafter = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull1);
    resourceInfoafter = PublicMethedForDailybuild.getAccountResource(contractExcAddress,
        blockingStubFull1);
    afterBalance = infoafter.getBalance();
    afterEnergyUsed = resourceInfoafter.getEnergyUsed();
    afterNetUsed = resourceInfoafter.getNetUsed();
    afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEnergyUsed:" + afterEnergyUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    nonexistentAddressAccount = PublicMethedForDailybuild
        .queryAccount(nonexistentAddress, blockingStubFull1);
    Assert.assertEquals(2, nonexistentAddressAccount.getBalance());
    Assert.assertEquals(0, infoById.get().getResultValue());

    Assert.assertEquals(energyUsageTotal2 + EnergyCost.getInstance().getNEW_ACCT_CALL(),
        energyUsageTotal);

  }


  @Test(enabled = true, description = "Transfer trx to myself")
  public void test004TransferTrxSelf() {

    Account info;

    AccountResourceMessage resourceInfo = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    String txid = "";
    String num = "1";

    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTrxSelf(uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

    Long fee = infoById.get().getFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long energyUsed = infoById.get().getReceipt().getEnergyUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    long energyUsageTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal);

    Account infoafter = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEnergyUsed:" + afterEnergyUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertEquals(contractResult.TRANSFER_FAILED, infoById.get().getReceipt().getResult());
    Assert.assertEquals("transfer trx failed: Cannot transfer TRX to yourself.",
        ByteArray.toStr(infoById.get().getResMessage().toByteArray()));

    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeEnergyUsed + energyUsed >= afterEnergyUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertNotEquals(10000000, energyUsageTotal);


  }


  @Test(enabled = true, description = "Transfer trx nonexistent target and insufficient balance")
  public void test005TransferTrxNonexistentTarget() {

    Account info;

    AccountResourceMessage resourceInfo = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    ECKey ecKey2 = new ECKey(Utils.getRandom());
    byte[] nonexistentAddress = ecKey2.getAddress();
    String txid = "";
    String num = "10000000" + ",\"" + Base58.encode58Check(nonexistentAddress) + "\"";

    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTrxNonexistentTarget(uint256,address)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull1);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

    Long fee = infoById.get().getFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long energyUsed = infoById.get().getReceipt().getEnergyUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    long energyUsageTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal);

    Account infoafter = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEnergyUsed:" + afterEnergyUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertEquals(contractResult.REVERT, infoById.get().getReceipt().getResult());
    Assert.assertEquals(
        "REVERT opcode executed",
        ByteArray.toStr(infoById.get().getResMessage().toByteArray()));

    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeEnergyUsed + energyUsed >= afterEnergyUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertNotEquals(10000000, energyUsageTotal);


  }


  @Test(enabled = true, description = "Transfer trx to myself and insufficient balance")
  public void test006TransferTrxSelf() {

    Account info;

    AccountResourceMessage resourceInfo = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    ECKey ecKey2 = new ECKey(Utils.getRandom());
    String txid = "";
    String num = "1000000000";

    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTrxSelf(uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

    Long fee = infoById.get().getFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long energyUsed = infoById.get().getReceipt().getEnergyUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    long energyUsageTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal);

    Account infoafter = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEnergyUsed:" + afterEnergyUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertEquals(contractResult.REVERT, infoById.get().getReceipt().getResult());
    Assert.assertEquals(
        "REVERT opcode executed",
        ByteArray.toStr(infoById.get().getResMessage().toByteArray()));

    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeEnergyUsed + energyUsed >= afterEnergyUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertNotEquals(10000000, energyUsageTotal);


  }

  @Test(enabled = true, description = "PreCompiled transfertoken with value,"
      + " long.max < value or long.min > value")
  public void test007TransferTrckenPreCompiled() {

    AccountResourceMessage resourceInfo = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull);
    Account info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);

    String txid = "";
    String num = "1";

    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTokenCompiledLongMax1()", "#", false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long energyUsed = infoById.get().getReceipt().getEnergyUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    long energyUsageTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal);

    resourceInfo = PublicMethedForDailybuild.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    beforeBalance = info.getBalance();
    beforeEnergyUsed = resourceInfo.getEnergyUsed();
    beforeNetUsed = resourceInfo.getNetUsed();
    beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);

    Assert.assertEquals(FAILED, infoById.get().getResult());
    Assert.assertTrue(energyUsageTotal < maxFeeLimit / 10);
    Assert.assertEquals("REVERT opcode executed", infoById.get().getResMessage().toStringUtf8());

    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTokenCompiledLongMin1()", "#", false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);
    fee = infoById.get().getFee();
    netUsed = infoById.get().getReceipt().getNetUsage();
    energyUsed = infoById.get().getReceipt().getEnergyUsage();
    netFee = infoById.get().getReceipt().getNetFee();
    energyUsageTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal);

    resourceInfo = PublicMethedForDailybuild.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    beforeBalance = info.getBalance();
    beforeEnergyUsed = resourceInfo.getEnergyUsed();
    beforeNetUsed = resourceInfo.getNetUsed();
    beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);

    Assert.assertEquals(FAILED, infoById.get().getResult());
    Assert.assertTrue(energyUsageTotal < maxFeeLimit / 10);
    Assert.assertEquals("REVERT opcode executed", infoById.get().getResMessage().toStringUtf8());

  }

  @Test(enabled = false, description = "PreCompiled tokenbalance")
  public void test008TransferTrctoken() {

    Assert.assertTrue(PublicMethedForDailybuild
        .sendcoin(contractExcAddress, 10000_000_000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);

    long start = System.currentTimeMillis() + 2000;
    long end = System.currentTimeMillis() + 1000000000;
    String description = Configuration.getByPath("testng.conf")
        .getString("defaultParameter.assetDescription");
    String url = Configuration.getByPath("testng.conf")
        .getString("defaultParameter.assetUrl");

    ByteString assetAccountId = null;
    final long TotalSupply = 10000000L;
    long now = System.currentTimeMillis();
    String tokenName = "testAssetIssue_" + Long.toString(now);

    //Create a new AssetIssue success.
//    Assert
//        .assertTrue(PublicMethedForDailybuild.createAssetIssue(contractExcAddress, tokenName, TotalSupply, 1,
//            10000, start, end, 1, description, url, 100000L,
//            100000L, 1L, 1L, contractExcKey, blockingStubFull));
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    assetAccountId = PublicMethedForDailybuild.queryAccount(contractExcAddress, blockingStubFull)
        .getAssetIssuedID();

    String filePath = "src/test/resources/soliditycode/TransferFailed001.sol";
    String contractName = "EnergyOfTransferFailedTest";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    contractAddress = PublicMethedForDailybuild
        .deployContract(contractName, abi, code, "", maxFeeLimit,
            0L, 100, null, contractExcKey,
            contractExcAddress, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethedForDailybuild.transferAsset(contractAddress,
        assetAccountId.toByteArray(), 100L, contractExcAddress, contractExcKey,
        blockingStubFull));

    Long returnAddressBytesAccountCountBefore = PublicMethedForDailybuild
        .getAssetIssueValue(contractAddress, assetAccountId, blockingStubFull);
    logger.info("returnAddressBytesAccountCountBefore : " + returnAddressBytesAccountCountBefore);
    Account info;

    AccountResourceMessage resourceInfo = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    Long testNetAccountCountBefore = PublicMethedForDailybuild
        .getAssetIssueValue(contractExcAddress, assetAccountId, blockingStubFull);
    Long contractAccountCountBefore = PublicMethedForDailybuild
        .getAssetIssueValue(contractAddress, assetAccountId, blockingStubFull);
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    logger.info("testNetAccountCountBefore:" + testNetAccountCountBefore);
    logger.info("contractAccountCountBefore:" + contractAccountCountBefore);
    String txid = "";
    String num =
        "\"" + Base58.encode58Check(contractAddress) + "\"," + "\"" + assetAccountId.toStringUtf8()
            + "\"";
    //String num = "\""+Base58.encode58Check(contractAddress) +"\","+ "\"" + -1 + "\"";
    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTokenTest(address,uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long energyUsed = infoById.get().getReceipt().getEnergyUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    long energyUsageTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal);

    Account infoafter = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    Long testNetAccountCountAfter = PublicMethedForDailybuild
        .getAssetIssueValue(contractExcAddress, assetAccountId, blockingStubFull);
    Long contractAccountCountAfter = PublicMethedForDailybuild
        .getAssetIssueValue(contractAddress, assetAccountId, blockingStubFull);
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEnergyUsed:" + afterEnergyUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("testNetAccountCountAfter:" + testNetAccountCountAfter);
    logger.info("contractAccountCountAfter:" + contractAccountCountAfter);

    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(energyUsageTotal < maxFeeLimit / 10);
    Assert.assertEquals(100, ByteArray.toInt(infoById.get().getContractResult(0).toByteArray()));


  }

  @Test(enabled = true, description = "PreCompiled address(0x1) query tokenbalance")
  public void test009TransferTrctoken() {
    //address: 410000000000000000000000000000000000000001
    String addressx = "T9yD14Nj9j7xAB4dbGeiX9h8unkKLxmGkn";
    byte[] addressxx = WalletClient.decodeFromBase58Check(addressx);

    Assert.assertTrue(PublicMethedForDailybuild
        .sendcoin(addressxx, 1000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));

    long start = System.currentTimeMillis() + 2000;
    long end = System.currentTimeMillis() + 1000000000;
    String description = Configuration.getByPath("testng.conf")
        .getString("defaultParameter.assetDescription");
    String url = Configuration.getByPath("testng.conf")
        .getString("defaultParameter.assetUrl");

    ByteString assetAccountId = ByteString.copyFromUtf8(tokenId);
    final long TotalSupply = 10000000L;
    long now = System.currentTimeMillis();
    String tokenName = "testAssetIssue_" + Long.toString(now);

    //Create a new AssetIssue success.
//    Assert
//        .assertTrue(PublicMethedForDailybuild.createAssetIssue(contractExcAddress, tokenName, TotalSupply, 1,
//            10000, start, end, 1, description, url, 100000L,
//            100000L, 1L, 1L, contractExcKey, blockingStubFull));
//    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);

    Assert.assertTrue(
        PublicMethedForDailybuild.transferAsset(contractExcAddress, assetAccountId.toByteArray(),
            10000000L, tokenOnwerAddress, tokenOwnerKey, blockingStubFull));
    assetAccountId = ByteString.copyFromUtf8(
        PublicMethedForDailybuild.queryAccount(contractExcAddress, blockingStubFull).getAssetV2Map()
            .keySet().toArray()[0].toString());

    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Long addressxxTokenValueBefore = PublicMethedForDailybuild
        .getAssetIssueValue(addressxx, assetAccountId, blockingStubFull);
    logger.info("addressxx-tokenvalue-before : " + addressxxTokenValueBefore);

    Assert.assertTrue(PublicMethedForDailybuild.transferAsset(addressxx,
        assetAccountId.toByteArray(), 100L, contractExcAddress, contractExcKey,
        blockingStubFull));
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Long addressxxTokenValueAfter = PublicMethedForDailybuild
        .getAssetIssueValue(addressxx, assetAccountId, blockingStubFull);
    logger.info("addressxx-tokenvalue-after : " + addressxxTokenValueAfter);
    Account info;

    AccountResourceMessage resourceInfo = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    Long testNetAccountCountBefore = PublicMethedForDailybuild
        .getAssetIssueValue(contractExcAddress, assetAccountId, blockingStubFull);
    Long contractAccountCountBefore = PublicMethedForDailybuild
        .getAssetIssueValue(contractAddress, assetAccountId, blockingStubFull);
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    logger.info("testNetAccountCountBefore:" + testNetAccountCountBefore);
    logger.info("contractAccountCountBefore:" + contractAccountCountBefore);
    String txid = "";
    //String num = "\""+Base58.encode58Check(contractAddress)
    // +"\","+ "\"" + assetAccountId.toStringUtf8() + "\"";
    String num = "\"" + assetAccountId.toStringUtf8() + "\"";
    //String num = "\""+Base58.encode58Check(contractAddress) +"\","+ "\"" + -1 + "\"";
    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTokenCompiledTokenId(uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long energyUsed = infoById.get().getReceipt().getEnergyUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    long energyUsageTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal);

    Account infoafter = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    Long testNetAccountCountAfter = PublicMethedForDailybuild
        .getAssetIssueValue(contractExcAddress, assetAccountId, blockingStubFull);
    Long contractAccountCountAfter = PublicMethedForDailybuild
        .getAssetIssueValue(contractAddress, assetAccountId, blockingStubFull);
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEnergyUsed:" + afterEnergyUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("testNetAccountCountAfter:" + testNetAccountCountAfter);
    logger.info("contractAccountCountAfter:" + contractAccountCountAfter);

    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(energyUsageTotal < maxFeeLimit / 10);
    Assert.assertEquals(addressxxTokenValueBefore + 100,
        ByteArray.toInt(infoById.get().getContractResult(0).toByteArray()));
  }

  @Test(enabled = true, description = "transferTrx to nonexistent target ,but revert")
  public void test010TransferRevert() {

    Account info;

    AccountResourceMessage resourceInfo = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    ECKey ecKey2 = new ECKey(Utils.getRandom());
    byte[] nonexistentAddress = ecKey2.getAddress();
    String txid = "";
    String num = "1" + ",\"" + Base58.encode58Check(nonexistentAddress) + "\"";

    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "testTransferTrxrevert(uint256,address)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

    Long fee = infoById.get().getFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long energyUsed = infoById.get().getReceipt().getEnergyUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    long energyUsageTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("energyUsed:" + energyUsed);
    logger.info("netFee:" + netFee);
    logger.info("energyUsageTotal:" + energyUsageTotal);

    Account infoafter = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethedForDailybuild
        .getAccountResource(contractExcAddress,
            blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterEnergyUsed = resourceInfoafter.getEnergyUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterEnergyUsed:" + afterEnergyUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Account nonexistentAddressAccount = PublicMethedForDailybuild
        .queryAccount(nonexistentAddress, blockingStubFull1);
    Assert.assertEquals(0, nonexistentAddressAccount.getBalance());
    Assert.assertEquals(1, infoById.get().getResultValue());

    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeEnergyUsed + energyUsed >= afterEnergyUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertTrue(energyUsageTotal > EnergyCost.getInstance().getNEW_ACCT_CALL());
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethedForDailybuild
        .freedResource(contractAddress, contractExcKey, testNetAccountAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelSolidity != null) {
      channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }


}
