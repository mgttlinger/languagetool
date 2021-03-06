/* LanguageTool, a natural language style checker
 * Copyright (C) 2012 Daniel Naber (http://www.danielnaber.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.server;

import org.languagetool.JLanguageTool;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Limit the maximum number of request per IP address for a given time range.
 */
class RequestLimiter {

  static final int REQUEST_QUEUE_SIZE = 1000;

  final List<RequestEvent> requestEvents = new CopyOnWriteArrayList<>();
  
  private final int requestLimit;
  private final int requestLimitInBytes;
  private final int requestLimitPeriodInSeconds;
  private final Long server;
  private DatabaseLogger logger;

  /**
   * @param requestLimit the maximum number of request per <tt>requestLimitPeriodInSeconds</tt>
   * @param requestLimitPeriodInSeconds the time period over which requests are considered, in seconds
   */
  RequestLimiter(int requestLimit, int requestLimitInBytes, int requestLimitPeriodInSeconds) {
    this.requestLimit = requestLimit;
    this.requestLimitInBytes = requestLimitInBytes;
    this.requestLimitPeriodInSeconds = requestLimitPeriodInSeconds;
    this.logger = DatabaseLogger.getInstance();
    if (this.logger.isLogging()) {
      DatabaseAccess db = DatabaseAccess.getInstance();
      this.server = db.getOrCreateServerId();
    } else {
      this.server = null;
    }
  }

  /**
   * The maximum number of request per {@link #getRequestLimitPeriodInSeconds()}.
   */
  int getRequestLimit() {
    return requestLimit;
  }

  /**
   * The maximum number of request bytes per {@link #getRequestLimitPeriodInSeconds()}.
   * @since 4.0
   */
  int getRequestLimitInBytes() {
    return requestLimitInBytes;
  }

  /**
   * The time period over which requests are considered, in seconds.
   */
  int getRequestLimitPeriodInSeconds() {
    return requestLimitPeriodInSeconds;
  }

  /**
   * @param ipAddress the client's IP address
   * @throws TooManyRequestsException if access is not allowed because the request limit is reached
   */
  void checkAccess(String ipAddress, Map<String, String> params) {
    int reqSize = getRequestSize(params);
    while (requestEvents.size() > REQUEST_QUEUE_SIZE) {
      requestEvents.remove(0);
    }
    requestEvents.add(new RequestEvent(ipAddress, new Date(), reqSize, ServerTools.getMode(params)));
    checkLimit(ipAddress);
  }

  private int getRequestSize(Map<String, String> params) {
    String text = params.get("text");
    if (text != null) {
      return text.length();
    } else {
      String data = params.get("data");
      if (data != null) {
        return data.length();
      }
    }
    return 0;
  }

  void checkLimit(String ipAddress) {
    int requestsByIp = 0;
    int requestSizeByIp = 0;
    // all requests before this date are considered old:
    Date thresholdDate = new Date(System.currentTimeMillis() - requestLimitPeriodInSeconds * 1000);
    for (RequestEvent event : requestEvents) {
      if (event.ip.equals(ipAddress) && event.date.after(thresholdDate)) {
        requestsByIp++;
        if (requestLimit > 0 && requestsByIp > requestLimit) {
          String msg = "limit: " + requestLimit + " / " + requestLimitPeriodInSeconds + ", requests: "  + requestsByIp + ", ip: " + ipAddress;
          logger.log(new DatabaseAccessLimitLogEntry("MaxRequestPerPeriod", server, null, null, msg, null, null));
          throw new TooManyRequestsException("Request limit of " + requestLimit + " requests per " +
                  requestLimitPeriodInSeconds + " seconds exceeded");
        }
        if (event.mode == JLanguageTool.Mode.TEXTLEVEL_ONLY) {
          requestSizeByIp += event.getSizeInBytes() / 10;    // text level rules cause much less load, so count them accordingly
          if (requestLimitInBytes > 0 && requestSizeByIp > requestLimitInBytes) {
            String msg = "limit in Mode.TEXTLEVEL_ONLY: " + requestLimitInBytes + " / " + requestLimitPeriodInSeconds + ", request size: "  + requestSizeByIp + ", ip: " + ipAddress;
            logger.log(new DatabaseAccessLimitLogEntry("MaxRequestSizePerPeriod", server, null, null, msg, null, null));
            throw new TooManyRequestsException("Request size limit of " + requestLimitInBytes + " bytes per " +
                    requestLimitPeriodInSeconds + " seconds exceeded in text-level checks");
          }
        } else {
          requestSizeByIp += event.getSizeInBytes();
          if (requestLimitInBytes > 0 && requestSizeByIp > requestLimitInBytes) {
            String msg = "limit: " + requestLimitInBytes + " / " + requestLimitPeriodInSeconds + ", request size: "  + requestSizeByIp + ", ip: " + ipAddress;
            logger.log(new DatabaseAccessLimitLogEntry("MaxRequestSizePerPeriod", server, null, null, msg, null, null));
            throw new TooManyRequestsException("Request size limit of " + requestLimitInBytes + " bytes per " +
                    requestLimitPeriodInSeconds + " seconds exceeded");
          }
        }
      }
    }
  }
  
  protected static class RequestEvent {

    private final String ip;
    private final Date date;
    private final int sizeInBytes;
    private final JLanguageTool.Mode mode;

    RequestEvent(String ip, Date date, int sizeInBytes, JLanguageTool.Mode mode) {
      this.ip = ip;
      this.date = new Date(date.getTime());
      this.sizeInBytes = sizeInBytes;
      this.mode = mode;
    }

    protected Date getDate() {
      return date;
    }
    
    int getSizeInBytes() {
      return sizeInBytes;
    }

  }

}
