/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 by luxe - https://github.com/de-luxe - BURST-LUXE-ZDVD-CX3E-3SM58
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package burstcoin.jminer.core.network.task;

import burstcoin.jminer.core.network.event.NetworkResultConfirmedEvent;
import burstcoin.jminer.core.network.event.NetworkResultErrorEvent;
import burstcoin.jminer.core.network.model.SubmitResultResponse;
import nxt.util.Convert;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * The type Network submit solo nonce task.
 */
@Component
@Scope("prototype")
public class NetworkSubmitSoloNonceTask
  implements Runnable
{
  private static final Logger LOG = LoggerFactory.getLogger(NetworkSubmitSoloNonceTask.class);

  @Autowired
  private ApplicationEventPublisher publisher;

  @Autowired
  private HttpClient httpClient;

  @Autowired
  private ObjectMapper objectMapper;

  private String soloServer;
  private String passPhrase;
  private long nonce;

  private long blockNumber;
  private long chunkPartStartNonce;
  private long calculatedDeadline;
  private long connectionTimeout;

  /**
   * Init void.
   *
   * @param blockNumber the block number
   * @param passPhrase the pass phrase
   * @param soloServer the solo server
   * @param connectionTimeout the connection timeout
   * @param nonce the nonce
   * @param chunkPartStartNonce the chunk part start nonce
   * @param calculatedDeadline the calculated deadline
   */
  public void init(long blockNumber, String passPhrase, String soloServer, long connectionTimeout, long nonce, long chunkPartStartNonce,
                   long calculatedDeadline)
  {
    this.connectionTimeout = connectionTimeout;

    this.soloServer = soloServer;
    this.passPhrase = passPhrase;
    this.nonce = nonce;

    this.blockNumber = blockNumber;
    this.chunkPartStartNonce = chunkPartStartNonce;
    this.calculatedDeadline = calculatedDeadline;
  }

  @Override
  public void run()
  {
    try
    {
      ContentResponse response = httpClient.POST(soloServer + "/burst")
        .param("requestType", "submitNonce")
        .param("secretPhrase", passPhrase)
        .param("nonce", Convert.toUnsignedLong(nonce))
        .timeout(connectionTimeout, TimeUnit.MILLISECONDS)
        .send();

      SubmitResultResponse result = objectMapper.readValue(response.getContentAsString(), SubmitResultResponse.class);

      if(result.getResult().equals("success"))
      {
        if(calculatedDeadline == result.getDeadline())
        {
          publisher.publishEvent(new NetworkResultConfirmedEvent(blockNumber, result.getDeadline(), nonce, chunkPartStartNonce));
        }
        else
        {
          publisher.publishEvent(new NetworkResultErrorEvent(blockNumber, nonce, calculatedDeadline, result.getDeadline(), chunkPartStartNonce));
        }
      }
      else
      {
        LOG.warn("Error: Submit solo nonce not successful: " + response.getContentAsString());
      }
    }
    catch(Exception e)
    {
      LOG.warn("Error: Failed to submit solo nonce: " + e.getMessage());
    }
  }
}