package org.tron.core.services.http;

import static org.tron.common.utils.Commons.decodeFromBase58Check;

import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.db.Manager;


@Component
@Slf4j(topic = "API")
public class GetRewardServlet extends RateLimiterServlet {

  @Autowired
  private Manager manager;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      long value = 0;
      byte[] address = getAddress(request);
      if (address != null) {
        value = manager.getDelegationService().queryReward(address);
      }
      response.getWriter().println("{\"reward\": " + value + "}");
    } catch (Exception e) {
      logger.error("", e);
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    doGet(request, response);
  }

  private byte[] getAddress(HttpServletRequest request) throws Exception {
    byte[] address = null;
    String addressParam = "address";
    String addressStr = request.getParameter(addressParam);
    if (StringUtils.isBlank(addressStr)) {
      String input = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(input);
      JSONObject jsonObject = JSONObject.parseObject(input);
      addressStr = jsonObject.getString(addressParam);
    }
    if (StringUtils.isNotBlank(addressStr)) {
      if (StringUtils.startsWith(addressStr, Constant.ADD_PRE_FIX_STRING_MAINNET)) {
        address = Hex.decode(addressStr);
      } else {
        address = decodeFromBase58Check(addressStr);
      }
    }
    return address;
  }
}
