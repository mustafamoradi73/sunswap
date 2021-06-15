package stest.tron.wallet.contract.scenario;

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
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Utils;
import org.tron.core.Wallet;
import org.tron.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.Parameter.CommonConstant;
import stest.tron.wallet.common.client.utils.PublicMethedForDailybuild;

@Slf4j
public class ContractScenario013 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethedForDailybuild.getFinalAddress(testKey002);
  byte[] contractAddress = null;
  String txid = "";
  Optional<TransactionInfo> infoById = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contract013Address = ecKey1.getAddress();
  String contract013Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

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
    PublicMethedForDailybuild.printAddress(contract013Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = true)
  public void deployTronTrxAndSunContract() {
    Assert.assertTrue(
        PublicMethedForDailybuild.sendcoin(contract013Address, 20000000000L, fromAddress,
            testKey002, blockingStubFull));
    AccountResourceMessage accountResource = PublicMethedForDailybuild
        .getAccountResource(contract013Address,
            blockingStubFull);
    Long energyLimit = accountResource.getEnergyLimit();
    Long energyUsage = accountResource.getEnergyUsed();

    logger.info("before energy limit is " + Long.toString(energyLimit));
    logger.info("before energy usage is " + Long.toString(energyUsage));

    String filePath = "./src/test/resources/soliditycode/contractScenario013.sol";
    String contractName = "timetest";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    txid = PublicMethedForDailybuild
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "",
            maxFeeLimit, 0L, 100, null, contract013Key, contract013Address, blockingStubFull);
    logger.info(txid);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    logger.info("energytotal is " + infoById.get().getReceipt().getEnergyUsageTotal());
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(infoById.get().getReceipt().getEnergyUsageTotal() > 0);
    Assert.assertFalse(infoById.get().getContractAddress().isEmpty());
  }

  @Test(enabled = true)
  public void triggerTronTrxAndSunContract() {
    AccountResourceMessage accountResource = PublicMethedForDailybuild
        .getAccountResource(contract013Address,
            blockingStubFull);
    Long energyLimit = accountResource.getEnergyLimit();
    Long energyUsage = accountResource.getEnergyUsed();

    logger.info("before energy limit is " + Long.toString(energyLimit));
    logger.info("before energy usage is " + Long.toString(energyUsage));

    String filePath = "./src/test/resources/soliditycode/contractScenario013.sol";
    String contractName = "timetest";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String txid = PublicMethedForDailybuild
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            0L, 100, null, contract013Key, contract013Address, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    logger.info("energytotal is " + infoById.get().getReceipt().getEnergyUsageTotal());

    contractAddress = infoById.get().getContractAddress().toByteArray();

    txid = PublicMethedForDailybuild.triggerContract(contractAddress,
        "time()", "#", false,
        0, 100000000L, contract013Address, contract013Key, blockingStubFull);
    logger.info(txid);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    logger.info("result is " + infoById.get().getResultValue());
    logger.info("energytotal is " + infoById.get().getReceipt().getEnergyUsageTotal());
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(infoById.get().getReceipt().getEnergyUsageTotal() > 0);
    Assert.assertTrue(infoById.get().getFee() == infoById.get().getReceipt().getEnergyFee());
    Assert.assertFalse(infoById.get().getContractAddress().isEmpty());
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


