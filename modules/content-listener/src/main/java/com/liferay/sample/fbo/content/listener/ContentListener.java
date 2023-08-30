package com.liferay.sample.fbo.content.listener;

import com.liferay.journal.model.JournalArticle;
import com.liferay.layout.content.LayoutContentProvider;
import com.liferay.layout.crawler.LayoutCrawler;
import com.liferay.layout.model.LayoutClassedModelUsage;
import com.liferay.layout.service.LayoutClassedModelUsageLocalService;
import com.liferay.layout.service.LayoutLocalizationLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.portal.kernel.search.Indexer;
import com.liferay.portal.kernel.search.IndexerRegistryUtil;
import com.liferay.portal.kernel.service.ClassNameLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.transaction.TransactionCommitCallbackUtil;
import com.liferay.portal.kernel.util.HtmlParser;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;

import java.util.List;
import java.util.Locale;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component(
	    immediate = true,
	    service = ModelListener.class
	)
public class ContentListener extends BaseModelListener<JournalArticle> {
	
	private long _journalArticleClassNameId;
	
	@Activate
	public void activate() {
		_journalArticleClassNameId = _classNameLocalService.getClassNameId(
				JournalArticle.class.getName());
	}
	
	@Override
	public void onAfterUpdate(JournalArticle originalModel, JournalArticle model) throws ModelListenerException {
		
		super.onAfterUpdate(originalModel, model);
		
		List<LayoutClassedModelUsage> layoutUsages = _layoutClassedModelUsageLocalService.getLayoutClassedModelUsages(
				_journalArticleClassNameId, 
				model.getResourcePrimKey());
		
		if(LOG.isDebugEnabled()) {
			LOG.debug("Looking for layout usages for {} {}", model.getResourcePrimKey(), _journalArticleClassNameId);
		}
		Indexer<Layout> layoutIndexer = IndexerRegistryUtil.getIndexer(Layout.class.getName());

		ServiceContext serviceContext = ServiceContextThreadLocal.getServiceContext();
		
		TransactionCommitCallbackUtil.registerCallback(() -> {
			
			layoutUsages.forEach(layoutUsage -> {
				long plid = layoutUsage.getPlid();
				
				try {
					Layout layout = _layoutLocalService.getLayout(plid);
					
					if(!layout.isDraftLayout()) {

						_layoutLocalizationLocalService.getLayoutLocalizations(plid).forEach(layoutLocalization -> {

							Locale locale = Locale.forLanguageTag(LocaleUtil.toW3cLanguageId(layoutLocalization.getLanguageId()));
							
							String content = StringPool.BLANK;

							try {
								if (LOG.isWarnEnabled()) {
									LOG.warn("Crawling layout content {} {}", layout.getPlid(), locale);
								}
								content = _layoutCrawler.getLayoutContent(layout, locale);
							}
							catch (Exception exception) {
								if (LOG.isWarnEnabled()) {
									LOG.warn("Unable to get layout content", exception);
								}
							}

							content = _getWrapper(content);

							if(LOG.isDebugEnabled()) {
								LOG.debug("Crawled content {}", content);
							}
							
							_layoutLocalizationLocalService.updateLayoutLocalization(
									content,
									LocaleUtil.toLanguageId(locale), layout.getPlid(),
									serviceContext);

						});
						
						layoutIndexer.reindex(layout);

						if(LOG.isDebugEnabled()) {
							LOG.debug("Reindexing layout {}", plid);
						}
						
					}
					
				} catch (PortalException e) {
					LOG.error("Failed to reindex layout {}", plid);
				}
			});
			
			
			return null;
		});
		
		
	}

	private String _getWrapper(String layoutContent) {
		int wrapperIndex = layoutContent.indexOf(_WRAPPER_ELEMENT);

		if (wrapperIndex == -1) {
			return layoutContent;
		}

		return _htmlParser.extractText(
			layoutContent.substring(wrapperIndex + _WRAPPER_ELEMENT.length()));
	}
	
	private static final String _WRAPPER_ELEMENT = "id=\"wrapper\">";
	
	@Reference
	private LayoutClassedModelUsageLocalService _layoutClassedModelUsageLocalService;
	
	@Reference
	private ClassNameLocalService _classNameLocalService;

	@Reference
	private LayoutLocalService _layoutLocalService;

	@Reference
	private LayoutLocalizationLocalService _layoutLocalizationLocalService;
	
	@Reference
	private LayoutContentProvider _layoutContentProvider;
	
	@Reference
	private Language _language;
	
	@Reference
	private HtmlParser _htmlParser;
	
	@Reference
	private Portal _portal;
	
	@Reference
	private LayoutCrawler _layoutCrawler;
	
	private static Logger LOG = LoggerFactory.getLogger(ContentListener.class);
	
}