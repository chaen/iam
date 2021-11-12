/**
 * Copyright (c) Istituto Nazionale di Fisica Nucleare (INFN). 2016-2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.infn.mw.iam.test.actuator;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.annotation.ProfileValueSourceConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import it.infn.mw.iam.test.util.NullSafeSystemProfileValueSource;
import it.infn.mw.iam.test.util.annotation.IamMockMvcIntegrationTest;

@RunWith(SpringRunner.class)
@IamMockMvcIntegrationTest
@IfProfileValue(name = "iam.offline", values = {"false", "<null>"})
@ProfileValueSourceConfiguration(NullSafeSystemProfileValueSource.class)
@Ignore("We no longer have a dedicated health endpoint for external connectivity tests")
public class ExternalServiceActuatorEndpointTests {

  private static final String ADMIN_USERNAME = "admin";
  private static final String ADMIN_ROLE = "ADMIN";

  private static final String USER_USERNAME = "test";
  private static final String USER_ROLE = "USER";

  private static final String STATUS_UP = "UP";

  @Value("${health.externalServiceProbe.path}")
  private String externalHealthEndpoint;

  @Value("${health.externalServiceProbe.endpoint}")
  private String checkedEndpoint;

  @Autowired
  private WebApplicationContext context;

  private MockMvc mvc;

  @Before
  public void setup() {
    mvc =
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).alwaysDo(log()).build();
  }

  @Test
  public void testExternalServicesHealthEndpoint() throws Exception {
    // @formatter:off
    mvc.perform(get(externalHealthEndpoint))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", equalTo(STATUS_UP)))
      .andExpect(jsonPath("$.external").doesNotExist());
    // @formatter:on
  }

  @Test
  @WithMockUser(username = USER_USERNAME, roles = {USER_ROLE})
  public void testExternalServicesHealthEndpointAsUser() throws Exception {
    // @formatter:off
    mvc.perform(get(externalHealthEndpoint))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", equalTo(STATUS_UP)))
      .andExpect(jsonPath("$.external").doesNotExist());
    // @formatter:on
  }

  @Test
  @WithMockUser(username = ADMIN_USERNAME, roles = {ADMIN_ROLE})
  public void testExternalServicesHealthEndpointAsAdmin() throws Exception {
    // @formatter:off
    mvc.perform(get(externalHealthEndpoint))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", equalTo(STATUS_UP)))
      .andExpect(jsonPath("$.external.status", equalTo(STATUS_UP)))
      .andExpect(jsonPath("$.external.location", equalTo(checkedEndpoint)))
      .andExpect(jsonPath("$.external.error").doesNotExist());
    // @formatter:on
  }

}
