package org.jahia.modules.contentintegrity.taglib;

import org.jahia.settings.SettingsBean;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class ToolsTokenTag extends TagSupport {

    private static final Logger logger = LoggerFactory.getLogger(ToolsTokenTag.class);
    private static final String CSRF_TOKENS_ATTR = "toolAccessTokens";
    private static final int MAX_TOKENS = 5000;
    public static final String TOKEN_PREFIX = "content-integrity-token-";
    private final long tokenExpiration;

    public ToolsTokenTag() {
        super();
        tokenExpiration = SettingsBean.getInstance().getLong("toolsTokenExpiration", 20L);
    }

    @Override
    public int doEndTag() throws JspException {
        final JSONObject token = getToken(pageContext.getSession());
        if (token != null) {
            try {
                final JspWriter out = pageContext.getOut();
                out.print(token.toString());
            } catch (IOException e) {
                logger.error("", e);
            }
        }

        return super.doEndTag();
    }

    private JSONObject getToken(HttpSession session) {
        final HashMap<String, Long> tokens = getCache(session);
        final String token = generateNewToken(tokens, session).get();

        try {
            return new JSONObject().put("token", token).put("expiration", tokens.get(token) + tokenExpiration);
        } catch (JSONException e) {
            logger.error("", e);
            return null;
        }
    }

    private Supplier<String> generateNewToken(HashMap<String, Long> tokens, HttpSession session) {
        return () -> {
            final String token = generateTokenKey();
            tokens.put(token, System.currentTimeMillis());

            if (tokens.size() > MAX_TOKENS) {
                tokens.remove(tokens.entrySet().stream().min(Map.Entry.comparingByValue()).orElseThrow(ArrayIndexOutOfBoundsException::new).getKey());
            }

            saveCache(session, tokens);

            return token;
        };
    }

    private String generateTokenKey() {
        return TOKEN_PREFIX.concat(UUID.randomUUID().toString());
    }

    private void saveCache(HttpSession session, HashMap<String, Long> tokens) {
        session.setAttribute(CSRF_TOKENS_ATTR, tokens);
    }

    @SuppressWarnings("unchecked")
    private HashMap<String, Long> getCache(HttpSession session) {
        HashMap<String, Long> tokensCache = (HashMap<String, Long>) session.getAttribute(CSRF_TOKENS_ATTR);

        if (tokensCache == null) {
            tokensCache = new HashMap<>();
            saveCache(session, tokensCache);
        }

        return tokensCache;
    }
}
