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
package it.infn.mw.iam.core.oauth;

import static org.mitre.openid.connect.request.ConnectRequestParameters.PROMPT;
import static org.mitre.openid.connect.request.ConnectRequestParameters.PROMPT_SEPARATOR;

import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.client.utils.URIBuilder;
import org.mitre.oauth2.model.ClientDetailsEntity;
import org.mitre.oauth2.model.SystemScope;
import org.mitre.oauth2.service.ClientDetailsEntityService;
import org.mitre.oauth2.service.SystemScopeService;
import org.mitre.openid.connect.model.UserInfo;
import org.mitre.openid.connect.service.ScopeClaimTranslationService;
import org.mitre.openid.connect.service.StatsService;
import org.mitre.openid.connect.service.UserInfoService;
import org.mitre.openid.connect.view.HttpCodeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;
import org.springframework.security.oauth2.provider.AuthorizationRequest;
import org.springframework.security.oauth2.provider.endpoint.RedirectResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;

import it.infn.mw.iam.api.account.AccountUtils;
import it.infn.mw.iam.api.common.NoSuchAccountError;
import it.infn.mw.iam.core.oauth.scope.pdp.ScopePolicyPDP;
import it.infn.mw.iam.persistence.model.IamAccount;

/**
 * @author jricher
 *
 */
@SuppressWarnings("deprecation")
@Controller
@SessionAttributes("authorizationRequest")
public class IamOAuthConfirmationController {


  @Autowired
  private ClientDetailsEntityService clientService;

  @Autowired
  private SystemScopeService scopeService;

  @Autowired
  private ScopeClaimTranslationService scopeClaimTranslationService;

  @Autowired
  private UserInfoService userInfoService;

  @Autowired
  private StatsService statsService;

  @Autowired
  private RedirectResolver redirectResolver;

  @Autowired
  private ScopePolicyPDP pdp;

  @Autowired
  private AccountUtils accountUtils;


  /**
   * Logger for this class
   */
  private static final Logger logger =
      LoggerFactory.getLogger(IamOAuthConfirmationController.class);

  public IamOAuthConfirmationController() {

  }

  public IamOAuthConfirmationController(ClientDetailsEntityService clientService) {
    this.clientService = clientService;
  }

  @PreAuthorize("hasRole('ROLE_USER')")
  @RequestMapping(path = "/oauth/confirm_access", method = RequestMethod.GET)
  public String confimAccess(Map<String, Object> model,
      @ModelAttribute("authorizationRequest") AuthorizationRequest authRequest,
      Authentication authUser, SessionStatus status) {

    // Check the "prompt" parameter to see if we need to do special processing

    String prompt = (String) authRequest.getExtensions().get(PROMPT);
    List<String> prompts = Splitter.on(PROMPT_SEPARATOR).splitToList(Strings.nullToEmpty(prompt));
    ClientDetailsEntity client = null;

    try {
      client = clientService.loadClientByClientId(authRequest.getClientId());
    } catch (OAuth2Exception e) {
      logger.error("confirmAccess: OAuth2Exception was thrown when attempting to load client", e);
      model.put(HttpCodeView.CODE, HttpStatus.BAD_REQUEST);
      return HttpCodeView.VIEWNAME;
    } catch (IllegalArgumentException e) {
      logger.error(
          "confirmAccess: IllegalArgumentException was thrown when attempting to load client", e);
      model.put(HttpCodeView.CODE, HttpStatus.BAD_REQUEST);
      return HttpCodeView.VIEWNAME;
    }

    if (client == null) {
      logger.error("confirmAccess: could not find client {}", authRequest.getClientId());
      model.put(HttpCodeView.CODE, HttpStatus.NOT_FOUND);
      return HttpCodeView.VIEWNAME;
    }

    if (prompts.contains("none")) {
      // if we've got a redirect URI then we'll send it

      String url = redirectResolver.resolveRedirect(authRequest.getRedirectUri(), client);

      try {
        URIBuilder uriBuilder = new URIBuilder(url);

        uriBuilder.addParameter("error", "interaction_required");
        if (!Strings.isNullOrEmpty(authRequest.getState())) {
          uriBuilder.addParameter("state", authRequest.getState()); // copy the state parameter if
                                                                    // one was given
        }

        status.setComplete();
        return "redirect:" + uriBuilder.toString();

      } catch (URISyntaxException e) {
        logger.error("Can't build redirect URI for prompt=none, sending error instead", e);
        model.put("code", HttpStatus.FORBIDDEN);
        return HttpCodeView.VIEWNAME;
      }
    }

    model.put("auth_request", authRequest);
    model.put("client", client);

    String redirectUri = authRequest.getRedirectUri();

    model.put("redirect_uri", redirectUri);


    // pre-process the scopes
    Set<SystemScope> scopes = scopeService.fromStrings(authRequest.getScope());

    Set<SystemScope> sortedScopes = new LinkedHashSet<>(scopes.size());
    Set<SystemScope> systemScopes = scopeService.getAll();

    // filter requested scopes according to the scope policy
    IamAccount account = accountUtils.getAuthenticatedUserAccount(authUser)
      .orElseThrow(() -> NoSuchAccountError.forUsername(authUser.getName()));

    Set<String> filteredScopes = pdp.filterScopes(scopeService.toStrings(scopes), account);

    // sort scopes for display based on the inherent order of system scopes
    for (SystemScope s : systemScopes) {
      if (scopeService.fromStrings(filteredScopes).contains(s)) {
        sortedScopes.add(s);
      }
    }

    // add in any scopes that aren't system scopes to the end of the list
    sortedScopes.addAll(Sets.difference(scopes, systemScopes));

    model.put("scopes", sortedScopes);

    // get the userinfo claims for each scope
    UserInfo user = userInfoService.getByUsername(authUser.getName());
    Map<String, Map<String, String>> claimsForScopes = new HashMap<>();
    if (user != null) {
      JsonObject userJson = user.toJson();

      for (SystemScope systemScope : sortedScopes) {
        Map<String, String> claimValues = new HashMap<>();

        Set<String> claims = scopeClaimTranslationService.getClaimsForScope(systemScope.getValue());
        for (String claim : claims) {
          if (userJson.has(claim) && userJson.get(claim).isJsonPrimitive()) {
            // TODO: this skips the address claim
            claimValues.put(claim, userJson.get(claim).getAsString());
          }
        }

        claimsForScopes.put(systemScope.getValue(), claimValues);
      }
    }

    model.put("claims", claimsForScopes);

    // client stats
    Integer count = statsService.getCountForClientId(client.getClientId()).getApprovedSiteCount();
    model.put("count", count);


    // contacts
    if (client.getContacts() != null) {
      String contacts = Joiner.on(", ").join(client.getContacts());
      model.put("contacts", contacts);
    }

    // if the client is over a week old and has more than one registration, don't give such a big
    // warning
    // instead, tag as "Generally Recognized As Safe" (gras)
    Date lastWeek = new Date(System.currentTimeMillis() - (60 * 60 * 24 * 7 * 1000));
    Boolean expression = count > 1 && client.getCreatedAt() != null && client.getCreatedAt().before(lastWeek);
    model.put("gras", expression);

    return "iam/approveClient";
  }

  /**
   * @return the clientService
   */
  public ClientDetailsEntityService getClientService() {
    return clientService;
  }

  /**
   * @param clientService the clientService to set
   */
  public void setClientService(ClientDetailsEntityService clientService) {
    this.clientService = clientService;
  }


}
