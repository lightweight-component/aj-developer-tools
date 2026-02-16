package org.foo.controller;

import com.ajaxjs.iam.UserConstants;
import com.ajaxjs.iam.client.BaseOidcClientUserController;
import com.ajaxjs.iam.client.CacheProvider;
import com.ajaxjs.iam.jwt.JwtAccessToken;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletResponse;

/**
 * 用户客户端
 */
@RestController
@RequestMapping("/client")
@RequiredArgsConstructor
public class ClientController extends BaseOidcClientUserController {
    private final CacheProvider cacheProvider;

    @Override
    public CacheProvider getCacheProvider() {
        return cacheProvider;
    }

    @Override
    public JwtAccessToken onAccessTokenGot(JwtAccessToken token, HttpServletResponse resp) {
        String tokenStr = token.getId_token();
        // 设置 Token 到 Cookie
        ResponseCookie cookie = ResponseCookie.from(UserConstants.ACCESS_TOKEN_KEY, tokenStr)
                .httpOnly(true)
                .secure(false) // TODO for prod
                .path("/")
                .sameSite("Strict") // 设置 SameSite 属性
                .maxAge(3600 * 24 * 3) // 有效期 1 小时
                .build();

        resp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return token;
    }

    @Value("${user.clientId}")
    private String clientId;

    @Value("${user.clientSecret}")
    private String clientSecret;

    @GetMapping("/to_login")
    public RedirectView loginPageUrl(@RequestParam(required = false) String web_url) {
        return loginPageUrl("http://localhost:8080/iam_api/oidc/authorization", clientId, "/api/client/callback", web_url);
    }

    @GetMapping("/callback")
    public ModelAndView callbackToken(@RequestParam String code, @RequestParam String state, @RequestParam(required = false) String web_url, HttpServletResponse resp) {
        return callbackToken(clientId, clientSecret, code, state, web_url, resp);
    }

}
