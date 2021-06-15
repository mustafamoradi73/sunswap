package org.tron.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.ProposalCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Transaction.Result.code;
import org.tron.protos.contract.ProposalContract.SideChainProposalCreateContract;

@Slf4j

public class ProposalCreateActuatorTest {

  private static final String dbPath = "output_ProposalCreate_test";
  private static final String ACCOUNT_NAME_FIRST = "ownerF";
  private static final String OWNER_ADDRESS_FIRST;
  private static final String ACCOUNT_NAME_SECOND = "ownerS";
  private static final String OWNER_ADDRESS_SECOND;
  private static final String URL = "https://tron.network";
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static final String OWNER_ADDRESS_NOACCOUNT;
  private static final String OWNER_ADDRESS_BALANCENOTSUFFIENT;
  private static TronApplicationContext context;
  private static Manager dbManager;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new TronApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS_FIRST =
        Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    OWNER_ADDRESS_SECOND =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    OWNER_ADDRESS_NOACCOUNT =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1aed";
    OWNER_ADDRESS_BALANCENOTSUFFIENT =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e06d4271a1ced";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
  }

  /**
   * Release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void initTest() {
    WitnessCapsule ownerWitnessFirstCapsule =
        new WitnessCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_FIRST)),
            10_000_000L,
            URL);
    AccountCapsule ownerAccountFirstCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(ACCOUNT_NAME_FIRST),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_FIRST)),
            AccountType.Normal,
            300_000_000L);
    AccountCapsule ownerAccountSecondCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(ACCOUNT_NAME_SECOND),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)),
            AccountType.Normal,
            200_000_000_000L);

    dbManager.getAccountStore()
        .put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
    dbManager.getAccountStore()
        .put(ownerAccountSecondCapsule.getAddress().toByteArray(), ownerAccountSecondCapsule);

    dbManager.getWitnessStore().put(ownerWitnessFirstCapsule.getAddress().toByteArray(),
        ownerWitnessFirstCapsule);

    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000000);
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(10);
    dbManager.getDynamicPropertiesStore().saveNextMaintenanceTime(2000000);
  }

  private Any getContract(String address, HashMap<Long, String> paras) {
    return Any.pack(
        SideChainProposalCreateContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
            .putAllParameters(paras)
            .build());
  }

  /**
   * first createProposal,result is success.
   */
  @Test
  public void successProposalCreate() {
    HashMap<Long, String> paras = new HashMap<>();
    paras.put(0L, String.valueOf(1000000L));
    ProposalCreateActuator actuator = new ProposalCreateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_FIRST, paras));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 0);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      long id = 1;
      ProposalCapsule proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
      Assert.assertNotNull(proposalCapsule);
      Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 1);
      Assert.assertEquals(proposalCapsule.getApprovals().size(), 0);
      Assert.assertEquals(proposalCapsule.getCreateTime(), 1000000);
      Assert.assertEquals(proposalCapsule.getExpirationTime(),
          261200000); // 2000000 + 3 * 4 * 21600000
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } catch (ItemNotFoundException e) {
      Assert.assertFalse(e instanceof ItemNotFoundException);
    }
  }

  /**
   * use Invalid Address, result is failed, exception is "Invalid address".
   */
  @Test
  public void invalidAddress() {
    HashMap<Long, String> paras = new HashMap<>();
    paras.put(0L, String.valueOf(10000L));
    ProposalCreateActuator actuator = new ProposalCreateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_INVALID, paras));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid address");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid address", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use AccountStore not exists, result is failed, exception is "account not exists".
   */
  @Test
  public void noAccount() {
    HashMap<Long, String> paras = new HashMap<>();
    paras.put(0L, String.valueOf(10000L));
    ProposalCreateActuator actuator = new ProposalCreateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_NOACCOUNT, paras));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("account[+OWNER_ADDRESS_NOACCOUNT+] not exists");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use WitnessStore not exists Address,result is failed,exception is "witness not exists".
   */
  @Test
  public void noWitness() {
    HashMap<Long, String> paras = new HashMap<>();
    paras.put(0L, String.valueOf(10000L));
    ProposalCreateActuator actuator = new ProposalCreateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_SECOND, paras));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("witness[+OWNER_ADDRESS_NOWITNESS+] not exists");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Witness[" + OWNER_ADDRESS_SECOND + "] not exists",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use invalid parameter, result is failed, exception is "Bad chain parameter id".
   */
  @Test
  public void invalidPara() {
    HashMap<Long, String> paras = new HashMap<>();
    paras.put(100L, String.valueOf(10000L));
    ProposalCreateActuator actuator = new ProposalCreateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_FIRST, paras));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("non-exist proposal number");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("non-exist proposal number 100",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    paras = new HashMap<>();
    paras.put(3L, String.valueOf(1 + 100_000_000_000_000_000L));
    actuator = new ProposalCreateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_FIRST, paras));
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Bad chain parameter value, valid range is [0,100_000_000_000_000_000L]");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Bad chain parameter value, valid range is [0,100000000000000000]",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    paras = new HashMap<>();
    paras.put(10L, String.valueOf(-1L));
    actuator = new ProposalCreateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_FIRST, paras));

    dbManager.getDynamicPropertiesStore().saveRemoveThePowerOfTheGr(-1);
    try {
      actuator.validate();
      fail("This proposal has been executed before and is only allowed to be executed once");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(
          "This proposal has been executed before and is only allowed to be executed once",
          e.getMessage());
    }

    paras.put(10L, String.valueOf(-1L));
    dbManager.getDynamicPropertiesStore().saveRemoveThePowerOfTheGr(0);
    actuator = new ProposalCreateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_FIRST, paras));

    dbManager.getDynamicPropertiesStore().saveRemoveThePowerOfTheGr(0);
    try {
      actuator.validate();
      fail("This value[REMOVE_THE_POWER_OF_THE_GR] is only allowed to be 1");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("This value[REMOVE_THE_POWER_OF_THE_GR] is only allowed to be 1",
          e.getMessage());
    }
  }

  /**
   * parameter size = 0 , result is failed, exception is "This proposal has no parameter.".
   */
  @Test
  public void emptyProposal() {
    HashMap<Long, String> paras = new HashMap<>();
    ProposalCreateActuator actuator = new ProposalCreateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_FIRST, paras));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("This proposal has no parameter");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("This proposal has no parameter.",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void InvalidParaValue() {
    HashMap<Long, String> paras = new HashMap<>();
    paras.put(10L, String.valueOf(1000L));
    ProposalCreateActuator actuator = new ProposalCreateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_FIRST, paras));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("This value[REMOVE_THE_POWER_OF_THE_GR] is only allowed to be 1");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("This value[REMOVE_THE_POWER_OF_THE_GR] is only allowed to be 1",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /*
   * two same proposal can work
   */
  @Test
  public void duplicateProposalCreateSame() {
    dbManager.getDynamicPropertiesStore().saveRemoveThePowerOfTheGr(0L);

    HashMap<Long, String> paras = new HashMap<>();
    paras.put(0L, String.valueOf(23 * 3600 * 1000L));
    paras.put(1L, String.valueOf(8_888_000_000L));
    paras.put(2L, String.valueOf(200_000L));
    paras.put(3L, String.valueOf(20L));
    paras.put(4L, String.valueOf(2048_000_000L));
    paras.put(5L, String.valueOf(64_000_000L));
    paras.put(6L, String.valueOf(64_000_000L));
    paras.put(7L, String.valueOf(64_000_000L));
    paras.put(8L, String.valueOf(64_000_000L));
    //paras.put(9L, String.valueOf(1L));
    paras.put(10L, String.valueOf(1L));
    paras.put(11L, String.valueOf(64L));
    paras.put(12L, String.valueOf(64L));
    paras.put(13L, String.valueOf(64L));

    ProposalCreateActuator actuator = new ProposalCreateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_FIRST, paras));

    ProposalCreateActuator actuatorSecond = new ProposalCreateActuator();
    actuatorSecond.setChainBaseManager(dbManager.getChainBaseManager())
        .setForkUtils(dbManager.getForkController())
        .setAny(getContract(OWNER_ADDRESS_FIRST, paras));

    dbManager.getDynamicPropertiesStore().saveLatestProposalNum(0L);
    Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 0);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);

      actuatorSecond.validate();
      actuatorSecond.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);

      Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 2L);
      ProposalCapsule proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(2L));
      Assert.assertNotNull(proposalCapsule);

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } catch (ItemNotFoundException e) {
      Assert.assertFalse(e instanceof ItemNotFoundException);
    }
  }

}