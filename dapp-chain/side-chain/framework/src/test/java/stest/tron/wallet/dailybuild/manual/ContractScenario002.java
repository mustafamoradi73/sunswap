package stest.tron.wallet.dailybuild.manual;

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
import org.tron.protos.contract.SmartContractOuterClass.SmartContract;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.Parameter.CommonConstant;
import stest.tron.wallet.common.client.utils.PublicMethedForDailybuild;

@Slf4j
public class ContractScenario002 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethedForDailybuild.getFinalAddress(testKey002);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contract002Address = ecKey1.getAddress();
  String contract002Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
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
    PublicMethedForDailybuild.printAddress(contract002Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

  }

  @Test(enabled = true, description = "Deploy contract with java-tron support interface")
  public void deployTronNative() {
    ECKey ecKey1 = new ECKey(Utils.getRandom());
    byte[] contract002Address = ecKey1.getAddress();
    String contract002Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

    Assert
        .assertTrue(PublicMethedForDailybuild.sendcoin(contract002Address, 500000000L, fromAddress,
            testKey002, blockingStubFull));
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethedForDailybuild.freezeBalanceGetEnergy(contract002Address, 1000000L,
        0, 1, contract002Key, blockingStubFull));
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    AccountResourceMessage accountResource = PublicMethedForDailybuild
        .getAccountResource(contract002Address,
            blockingStubFull);
    Long energyLimit = accountResource.getEnergyLimit();
    Long energyUsage = accountResource.getEnergyUsed();
    Long balanceBefore = PublicMethedForDailybuild.queryAccount(contract002Key, blockingStubFull)
        .getBalance();

    logger.info("before energy limit is " + Long.toString(energyLimit));
    logger.info("before energy usage is " + Long.toString(energyUsage));
    logger.info("before balance is " + Long.toString(balanceBefore));

    String contractName = "TronNative";
    String filePath = "./src/test/resources/soliditycode/contractScenario002.sol";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String txid = PublicMethedForDailybuild
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "",
            maxFeeLimit, 0L, 100, null, contract002Key, contract002Address, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull1);

    logger.info(txid);
    Optional<TransactionInfo> infoById = PublicMethedForDailybuild
        .getTransactionInfoById(txid, blockingStubFull);
    com.google.protobuf.ByteString contractAddress = infoById.get().getContractAddress();
    SmartContract smartContract = PublicMethedForDailybuild
        .getContract(contractAddress.toByteArray(), blockingStubFull);
    Assert.assertTrue(smartContract.getAbi() != null);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull1);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    accountResource = PublicMethedForDailybuild
        .getAccountResource(contract002Address, blockingStubFull1);
    energyLimit = accountResource.getEnergyLimit();
    energyUsage = accountResource.getEnergyUsed();
    Long balanceAfter = PublicMethedForDailybuild
        .queryAccount(contract002Address, blockingStubFull1)
        .getBalance();

    logger.info("after energy limit is " + Long.toString(energyLimit));
    logger.info("after energy usage is " + Long.toString(energyUsage));
    logger.info("after balance is " + Long.toString(balanceAfter));
    logger.info("transaction fee is " + Long.toString(infoById.get().getFee()));

    Assert.assertTrue(energyUsage > 0);
    Assert.assertTrue(balanceBefore == balanceAfter + infoById.get().getFee());
    PublicMethedForDailybuild.unFreezeBalance(contract002Address, contract002Key, 1,
        contract002Address, blockingStubFull);

  }

  @Test(enabled = true, description = "Get smart contract with invalid address")
  public void getContractWithInvalidAddress() {
    byte[] contractAddress = contract002Address;
    SmartContract smartContract = PublicMethedForDailybuild
        .getContract(contractAddress, blockingStubFull);
    logger.info(smartContract.getAbi().toString());
    Assert.assertTrue(smartContract.getAbi().toString().isEmpty());
  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethedForDailybuild
        .freedResource(contract002Address, contract002Key, fromAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


