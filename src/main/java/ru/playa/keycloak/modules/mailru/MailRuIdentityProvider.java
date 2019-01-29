package ru.playa.keycloak.modules.mailru;

import com.fasterxml.jackson.databind.JsonNode;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import ru.playa.keycloak.modules.AbstractRussianOAuth2IdentityProvider;
import ru.playa.keycloak.modules.MessageUtils;
import ru.playa.keycloak.modules.StringUtils;

import java.io.IOException;

import static ru.playa.keycloak.modules.MD5Utils.md5;

/**
 * Провайдер OAuth-авторизации через <a href="https://my.mail.ru">Мой Мир</a>.
 * <a href="https://api.mail.ru/docs/guides/oauth/">Подробнее</a>.
 *
 * @author Anatoliy Pokhresnyi
 */
public class MailRuIdentityProvider
        extends AbstractRussianOAuth2IdentityProvider<MailRuIdentityProviderConfig>
        implements SocialIdentityProvider<MailRuIdentityProviderConfig> {

    /**
     * Запрос кода подтверждения.
     */
    private static final String AUTH_URL = "https://connect.mail.ru/oauth/authorize";

    /**
     * Обмен кода подтверждения на токен.
     */
    private static final String TOKEN_URL = "https://connect.mail.ru/oauth/token";

    /**
     * Запрос информации о пользователе.
     */
    private static final String PROFILE_URL = "http://www.appsmail.ru/platform/api";

    /**
     * Права доступа к данным пользователя по умолчанию.
     */
    private static final String DEFAULT_SCOPE = "";

    /**
     * Создает объект OAuth-авторизации через
     * <a href="https://my.mail.ru">Мой Мир</a>.
     *
     * @param session Сессия Keycloak.
     * @param config  Конфигурация OAuth-авторизации.
     */
    public MailRuIdentityProvider(KeycloakSession session, MailRuIdentityProviderConfig config) {
        super(session, config);
        config.setAuthorizationUrl(AUTH_URL);
        config.setTokenUrl(TOKEN_URL);
        config.setUserInfoUrl(PROFILE_URL);
    }

    @Override
    protected boolean supportsExternalExchange() {
        return true;
    }

    @Override
    protected String getProfileEndpointForValidation(EventBuilder event) {
        return PROFILE_URL;
    }

    @Override
    protected SimpleHttp buildUserInfoRequest(String subjectToken, String userInfoUrl) {
        return SimpleHttp.doGet(PROFILE_URL + "?access_token=" + subjectToken, session);
    }

    @Override
    protected BrokeredIdentityContext extractIdentityFromProfile(EventBuilder event, JsonNode profile) {

        BrokeredIdentityContext user = new BrokeredIdentityContext(getJsonProperty(profile, "uid"));

        String email = getJsonProperty(profile, "email");
        if (StringUtils.isNullOrEmpty(email)) {
            throw new IllegalArgumentException(MessageUtils.email("MailRu"));
        }

        String nick = getJsonProperty(profile, "nick");
        if (StringUtils.isNullOrEmpty(nick)) {
            user.setUsername(email);
        } else {
            user.setUsername(nick);
        }

        user.setEmail(email);
        user.setFirstName(getJsonProperty(profile, "first_name"));
        user.setLastName(getJsonProperty(profile, "last_name"));

        user.setIdpConfig(getConfig());
        user.setIdp(this);

        AbstractJsonUserAttributeMapper.storeUserProfileForMapper(user, profile, getConfig().getAlias());

        return user;
    }

    @Override
    protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {
        try {
            String params = "app_id="
                    + getConfig().getClientId()
                    + "&method=users.getInfo"
                    + "&secure=1"
                    + "&session_key="
                    + accessToken
                    + getConfig().getClientSecret();

            String url = PROFILE_URL
                    + "?method=users.getInfo"
                    + "&secure=1"
                    + "&app_id="
                    + getConfig().getClientId()
                    + "&session_key="
                    + accessToken
                    + "&sig="
                    + md5(params);

            return extractIdentityFromProfile(null, SimpleHttp.doGet(url, session).asJson());

        } catch (IOException e) {
            throw new IdentityBrokerException("Could not obtain user profile from MailRu: " + e.getMessage(), e);
        }
    }

    @Override
    protected String getDefaultScopes() {
        return DEFAULT_SCOPE;
    }
}