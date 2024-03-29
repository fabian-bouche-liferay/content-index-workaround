package com.liferay.sample.fbo.content.listener;

import com.liferay.layout.crawler.LayoutCrawler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.CookieKeys;
import com.liferay.portal.kernel.util.Http;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;

import java.net.InetAddress;

import java.util.Locale;
import java.util.Objects;

import javax.servlet.http.Cookie;

import org.apache.http.HttpStatus;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Pavel Savinov
 */
@Component(immediate = true, service = LayoutCrawler.class)
public class LayoutCrawlerImpl implements LayoutCrawler {

	@Override
	public String getLayoutContent(Layout layout, Locale locale)
		throws Exception {

		InetAddress inetAddress = _portal.getPortalServerInetAddress(
			_isHttpsEnabled());

		if (inetAddress == null) {
			return StringPool.BLANK;
		}

		Company company = _companyLocalService.getCompany(
			layout.getCompanyId());

		Http.Options options = new Http.Options();

		options.addHeader(HttpHeaders.USER_AGENT, _USER_AGENT);
		options.addHeader("Host", company.getVirtualHostname());

		Cookie cookie = new Cookie(
			CookieKeys.GUEST_LANGUAGE_ID, LocaleUtil.toLanguageId(locale));

		cookie.setDomain(inetAddress.getHostName());

		options.setCookies(new Cookie[] {cookie});

		ThemeDisplay themeDisplay = _getThemeDisplay(
			company, layout, locale,
			inetAddress);

		options.setLocation(
			HttpComponentsUtil.addParameter(
				_portal.getLayoutFullURL(layout, themeDisplay), "p_l_mode",
				Constants.SEARCH));

		if(LOG.isDebugEnabled()) {
			LOG.debug(options.getLocation());
		}
		
		options.setTimeout(100);
		
		String response = _http.URLtoString(options);

		Http.Response httpResponse = options.getResponse();

		if (httpResponse.getResponseCode() == HttpStatus.SC_OK) {
			return response;
		}

		return StringPool.BLANK;
	}

	private String _getI18nPath(Locale locale) {
		Locale defaultLocale = _language.getLocale(locale.getLanguage());

		if (LocaleUtil.equals(defaultLocale, locale)) {
			return StringPool.SLASH + defaultLocale.getLanguage();
		}

		return StringPool.SLASH + locale.toLanguageTag();
	}

	private ThemeDisplay _getThemeDisplay(
			Company company, Layout layout,			
			Locale locale, InetAddress inetAddress)
		throws Exception {

		ThemeDisplay themeDisplay = new ThemeDisplay();

		themeDisplay.setCompany(company);
		themeDisplay.setI18nLanguageId(locale.toString());
		themeDisplay.setI18nPath(_getI18nPath(locale));
		themeDisplay.setLanguageId(LocaleUtil.toLanguageId(locale));
		themeDisplay.setLayout(layout);
		themeDisplay.setLayoutSet(layout.getLayoutSet());
		themeDisplay.setLocale(locale);
		themeDisplay.setScopeGroupId(layout.getGroupId());

		themeDisplay.setServerName(inetAddress.getHostName());
		themeDisplay.setServerPort(_portal.getPortalServerPort(_isHttpsEnabled()));

		themeDisplay.setSiteGroupId(layout.getGroupId());

		return themeDisplay;
	}

	private boolean _isHttpsEnabled() {
		if (Objects.equals(
				Http.HTTPS,
				PropsUtil.get(PropsKeys.PORTAL_INSTANCE_PROTOCOL)) ||
			Objects.equals(
				Http.HTTPS, PropsUtil.get(PropsKeys.WEB_SERVER_PROTOCOL))) {

			return true;
		}

		return false;
	}

	private static final String _USER_AGENT = "Liferay Page Crawler";

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private ConfigurationProvider _configurationProvider;

	@Reference
	private Http _http;

	@Reference
	private Language _language;

	@Reference
	private Portal _portal;
	
	private static Logger LOG = LoggerFactory.getLogger(LayoutCrawlerImpl.class);

}