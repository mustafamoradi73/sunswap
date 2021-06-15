package stest.tron.wallet.dailybuild.tvmnewcommand.transferfailed;

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
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.Parameter.CommonConstant;
import stest.tron.wallet.common.client.utils.PublicMethedForDailybuild;

@Slf4j
public class TransferFailed007 {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] testNetAccountAddress = PublicMethedForDailybuild
      .getFinalAddress(testNetAccountKey);
  private final Long maxFeeLimit = Configuration.getByPath("testng.cong")
      .getLong("defaultParameter.maxFeeLimit");
  byte[] contractAddress = null;
  byte[] contractAddress1 = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] accountExcAddress = ecKey1.getAddress();
  String accountExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
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

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethedForDailybuild.printAddress(accountExcKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

  }

  @Test(enabled = false, description = "Deploy contract for trigger")
  public void deployContract() {
    Assert.assertTrue(PublicMethedForDailybuild
        .sendcoin(accountExcAddress, 10000000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);

    String filePath = "src/test/resources/soliditycode/TransferFailed007.sol";
    String contractName = "EnergyOfTransferFailedTest";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String Txid1 = PublicMethedForDailybuild
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit, 0L, 100L,
            null, accountExcKey, accountExcAddress, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethedForDailybuild
        .getTransactionInfoById(Txid1, blockingStubFull);
    contractAddress = infoById.get().getContractAddress().toByteArray();
    Assert.assertEquals(0, infoById.get().getResultValue());
  }

  @Test(enabled = false, description = "TransferFailed for create2")
  public void triggerContract() {
    Account info;

    AccountResourceMessage resourceInfo = PublicMethedForDailybuild
        .getAccountResource(accountExcAddress,
            blockingStubFull);
    info = PublicMethedForDailybuild.queryAccount(accountExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeEnergyUsed = resourceInfo.getEnergyUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeEnergyUsed:" + beforeEnergyUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);

    Assert.assertTrue(PublicMethedForDailybuild
        .sendcoin(contractAddress, 15L, accountExcAddress, accountExcKey, blockingStubFull));
    logger.info(
        "contractAddress balance before: " + PublicMethedForDailybuild
            .queryAccount(contractAddress, blockingStubFull)
            .getBalance());

    String filePath = "./src/test/resources/soliditycode/TransferFailed007.sol";
    String contractName = "Caller";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);
    String testContractCode = retMap.get("byteCode").toString();
    Long salt = 1L;

    String param = "\"" + testContractCode + "\"," + salt;

    String triggerTxid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "deploy(bytes,uint256)", param, false, 0L,
        maxFeeLimit, accountExcAddress, accountExcKey, blockingStubFull);

    Optional<TransactionInfo> infoById = PublicMethedForDailybuild
        .getTransactionInfoById(triggerTxid, blockingStubFull);

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

    long afterBalance = 0L;
    afterBalance = PublicMethedForDailybuild.queryAccount(contractAddress, blockingStubFull)
        .getBalance();
    logger.info(
        "contractAddress balance after : " + PublicMethedForDailybuild
            .queryAccount(contractAddress, blockingStubFull)
            .getBalance());
    Assert.assertEquals(0, infoById.get().getResultValue());
    Assert.assertEquals("SUCESS", infoById.get().getResult().toString());
    Assert.assertEquals(5L, afterBalance);
    Assert.assertFalse(infoById.get().getInternalTransactions(0).getRejected());
    Assert.assertTrue(infoById.get().getReceipt().getEnergyUsageTotal() < 10000000);

    triggerTxid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "deploy(bytes,uint256)", param, false, 0L,
        maxFeeLimit, accountExcAddress, accountExcKey, blockingStubFull);

    infoById = PublicMethedForDailybuild
        .getTransactionInfoById(triggerTxid, blockingStubFull);

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

    afterBalance = PublicMethedForDailybuild.queryAccount(contractAddress, blockingStubFull)
        .getBalance();
    logger.info(
        "contractAddress balance after : " + PublicMethedForDailybuild
            .queryAccount(contractAddress, blockingStubFull)
            .getBalance());
    Assert.assertEquals(0, infoById.get().getResultValue());
    Assert.assertEquals("SUCESS", infoById.get().getResult().toString());
    Assert.assertEquals(5L, afterBalance);
    Assert.assertEquals(0, ByteArray.toInt(infoById.get().getContractResult(0).toByteArray()));
    Assert.assertTrue(infoById.get().getReceipt().getEnergyUsageTotal() < 10000000);

  }

  /**
   * constructor.
   */
  @AfterClass

  public void shutdown() throws InterruptedException {
    PublicMethedForDailybuild
        .freedResource(accountExcAddress, accountExcKey, testNetAccountAddress, blockingStubFull);

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
