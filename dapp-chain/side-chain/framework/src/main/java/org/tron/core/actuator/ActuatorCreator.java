package org.tron.core.actuator;

import com.google.common.collect.Lists;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.ForkUtils;
import org.tron.core.ChainBaseManager;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.store.DynamicPropertiesStore;
import org.tron.core.store.StoreFactory;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction.Contract;

@Slf4j(topic = "actuator")
public class ActuatorCreator {

  private static ActuatorCreator INSTANCE;

  private DynamicPropertiesStore dynamicPropertiesStore;

  private ForkUtils forkUtils = new ForkUtils();

  private ChainBaseManager chainBaseManager;

  private ActuatorCreator(StoreFactory storeFactory) {
    chainBaseManager = storeFactory.getChainBaseManager();
    dynamicPropertiesStore = storeFactory.getChainBaseManager().getDynamicPropertiesStore();
    forkUtils.setDynamicPropertiesStore(dynamicPropertiesStore);
  }

  public static ActuatorCreator getINSTANCE() {
    if (ActuatorCreatorInner.INSTANCE == null) {
      ActuatorCreatorInner.INSTANCE = new ActuatorCreator(StoreFactory.getInstance());
    }
    return ActuatorCreatorInner.INSTANCE;
  }

  public static void init() {
    ActuatorCreatorInner.INSTANCE = new ActuatorCreator(StoreFactory.getInstance());
  }

  /**
   * create actuator.
   */
  public List<Actuator> createActuator(TransactionCapsule transactionCapsule)
      throws ContractValidateException {
    List<Actuator> actuatorList = Lists.newArrayList();
    if (null == transactionCapsule || null == transactionCapsule.getInstance()) {
      logger.info("transactionCapsule or Transaction is null");
      return actuatorList;
    }

    Protocol.Transaction.raw rawData = transactionCapsule.getInstance().getRawData();
    for (Contract contract : rawData.getContractList()) {
      try {
        actuatorList.add(getActuatorByContract(contract, transactionCapsule));
      } catch (Exception e) {
        logger.error("", e);
        throw new ContractValidateException(e.getMessage());
      }
    }
    return actuatorList;
  }

  private Actuator getActuatorByContract(Contract contract,
      TransactionCapsule tx)
      throws IllegalAccessException, InstantiationException, ContractValidateException {
    Class<? extends Actuator> clazz = TransactionFactory.getActuator(contract.getType());
    if (clazz == null) {
      throw new ContractValidateException("not exist contract " + contract);
    }
    AbstractActuator abstractActuator = (AbstractActuator) clazz.newInstance();
    abstractActuator.setChainBaseManager(chainBaseManager).setContract(contract)
        .setForkUtils(forkUtils).setTx(tx);
    return abstractActuator;
  }

  private static class ActuatorCreatorInner {

    private static ActuatorCreator INSTANCE;
  }
}
