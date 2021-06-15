package stest.tron.wallet.dailybuild.tvmnewcommand.batchValidateSignContract;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.tron.api.WalletGrpc;
import org.tron.api.WalletSolidityGrpc;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Hash;
import org.tron.common.utils.Utils;
import org.tron.common.utils.WalletUtil;
import org.tron.core.Wallet;
import org.tron.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.Parameter;
import stest.tron.wallet.common.client.utils.AbiUtil;
import stest.tron.wallet.common.client.utils.PublicMethedForDailybuild;

@Slf4j
public class batchValidateSignContract012 {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] testNetAccountAddress = PublicMethedForDailybuild
      .getFinalAddress(testNetAccountKey);
  byte[] contractAddress = null;
  String txid;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contractExcAddress = ecKey1.getAddress();
  String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethedForDailybuild.printAddress(contractExcKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1).usePlaintext(true).build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
    txid = PublicMethedForDailybuild
        .sendcoinGetTransactionId(contractExcAddress, 10000000000L, testNetAccountAddress,
            testNetAccountKey, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    String filePath = "src/test/resources/soliditycode/batchvalidatesign005.sol";
    String contractName = "Demo";
    HashMap retMap = PublicMethedForDailybuild.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethedForDailybuild
        .deployContract(contractName, abi, code, "", maxFeeLimit, 0L, 100, null, contractExcKey,
            contractExcAddress, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
  }

  @Test(enabled = true, description = "Trigger precompile multivalisign function with correct data")
  public void test01TriggerPrecompileMultivalisignWithCorrectData() {
    List<Object> signatures = new ArrayList<>();
    List<Object> addresses = new ArrayList<>();
    byte[] hash = Hash.sha3(txid.getBytes());
    for (int i = 0; i < 16; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      signatures.add(Hex.toHexString(sign));
      addresses.add(WalletUtil.encode58Check(key.getAddress()));
    }
    List<Object> parameters = Arrays.asList("0x" + Hex.toHexString(hash), signatures, addresses);
    String argsStr = PublicMethedForDailybuild.parametersString(parameters);

    String input = AbiUtil.parseParameters("batchvalidatesign(bytes32,bytes[],address[])", argsStr);
    String method = "testArray2(bytes)";
    txid = PublicMethedForDailybuild.triggerContract(contractAddress, method,
        AbiUtil.parseParameters(method, Arrays.asList(input)), true, 0, maxFeeLimit,
        contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethedForDailybuild.getTransactionById(txid, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(0, infoById.get().getResultValue());
    logger.info(
        "infoById:" + ByteArray.toHexString(infoById.get().getContractResult(0).toByteArray()));
    Assert.assertTrue(ByteArray.toHexString(infoById.get().getContractResult(0).toByteArray())
        .equals("0000000000000000000000000000000000000000000000000000000000000001"
            + "0000000000000000000000000000000000000000000000000000000000000040"
            + "0000000000000000000000000000000000000000000000000000000000000020"
            + "0101010101010101010101010101010100000000000000000000000000000000"));
  }

  @Test(enabled = true, description = "Trigger precompile multivalidatesign function with incor"
      + "rect data")
  public void test02TriggerPrecompileMultivalisignWithIncorrectData() {
    List<Object> signatures = new ArrayList<>();
    List<Object> addresses = new ArrayList<>();
    byte[] hash = Hash.sha3(txid.getBytes());
    for (int i = 0; i < 16; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      signatures.add(Hex.toHexString(sign));
      addresses.add(WalletUtil.encode58Check(key.getAddress()));
    }
    byte[] sign = new ECKey().sign(Hash.sha3("sdifhsdfihyw888w7".getBytes())).toByteArray();
    signatures.set(0, Hex.toHexString(sign));
    List<Object> parameters = Arrays.asList("0x" + Hex.toHexString(hash), signatures, addresses);
    String argsStr = PublicMethedForDailybuild.parametersString(parameters);

    String input = AbiUtil.parseParameters("batchvalidatesign(bytes32,bytes[],address[])", argsStr);
    String method = "testArray2(bytes)";
    txid = PublicMethedForDailybuild.triggerContract(contractAddress, method,
        AbiUtil.parseParameters(method, Arrays.asList(input)), true, 0, maxFeeLimit,
        contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethedForDailybuild.getTransactionById(txid, blockingStubFull);
    PublicMethedForDailybuild.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethedForDailybuild.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(0, infoById.get().getResultValue());
    logger.info(
        "infoById:" + ByteArray.toHexString(infoById.get().getContractResult(0).toByteArray()));
    Assert.assertTrue(ByteArray.toHexString(infoById.get().getContractResult(0).toByteArray())
        .equals("0000000000000000000000000000000000000000000000000000000000000001"
            + "0000000000000000000000000000000000000000000000000000000000000040"
            + "0000000000000000000000000000000000000000000000000000000000000020"
            + "0001010101010101010101010101010100000000000000000000000000000000"));
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    long balance = PublicMethedForDailybuild.queryAccount(contractExcKey, blockingStubFull)
        .getBalance();
    PublicMethedForDailybuild
        .sendcoin(testNetAccountAddress, balance, contractExcAddress, contractExcKey,
            blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

}
