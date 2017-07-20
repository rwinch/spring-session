package thymeleaf;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.result.view.AbstractView;
import org.springframework.web.reactive.result.view.RequestContext;
import org.springframework.web.server.ServerWebExchange;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.context.IContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring5.ISpringWebFluxTemplateEngine;
import org.thymeleaf.spring5.context.webflux.IReactiveDataDriverContextVariable;
import org.thymeleaf.spring5.context.webflux.ReactiveDataDriverContextVariable;
import org.thymeleaf.spring5.context.webflux.ReactiveLazyContextVariable;
import org.thymeleaf.spring5.context.webflux.SpringWebFluxExpressionContext;
import org.thymeleaf.spring5.expression.ThymeleafEvaluationContext;
import org.thymeleaf.spring5.naming.SpringContextVariableNames;
import org.thymeleaf.standard.expression.FragmentExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;
import org.thymeleaf.standard.expression.StandardExpressions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


/**
 * <p>
 *   Base implementation of the Spring WebFlux {@link org.springframework.web.reactive.result.view.View}
 *   interface.
 * </p>
 * <p>
 *   Views represent a template being executed, after being resolved (and
 *   instantiated) by a {@link org.springframework.web.reactive.result.view.ViewResolver}.
 * </p>
 * <p>
 *   This is the default view implementation resolved by {@link ThymeleafReactiveViewResolver}.
 * </p>
 * <p>
 *   This view needs a {@link ISpringWebFluxTemplateEngine} for execution, and it will call its
 *   {@link ISpringWebFluxTemplateEngine#processStream(String, Set, IContext, DataBufferFactory, MediaType, Charset, int)}
 *   method to create the reactive data streams to be used for processing the template. See the documentation
 *   of this class to know more about the different operation modes available.
 * </p>
 *
 * @see ThymeleafReactiveViewResolver
 * @see ISpringWebFluxTemplateEngine
 * @see ReactiveDataDriverContextVariable
 * @see IReactiveDataDriverContextVariable
 *
 * @author Daniel Fern&aacute;ndez
 *
 * @since 3.0.3
 *
 */
public class ThymeleafReactiveView extends AbstractView implements BeanNameAware {


	protected static final Logger logger = LoggerFactory.getLogger(ThymeleafReactiveView.class);

	public static final int DEFAULT_RESPONSE_CHUNK_SIZE_BYTES = Integer.MAX_VALUE;


	private String beanName = null;
	private ISpringWebFluxTemplateEngine templateEngine = null;
	private String templateName = null;
	private Locale locale = null;
	private Map<String, Object> staticVariables = null;

	// These two flags are meant to determine if these fields have been specifically set a
	// value for this View object, so that we know that the ViewResolver should not be
	// overriding them with its own view-resolution-wide values.
	private boolean defaultCharsetSet = false;
	private boolean supportedMediaTypesSet = false;

	private Set<String> markupSelectors = null;



	// This will determine whether we will be throttling or not, and if so the maximum size of the chunks that will be
	// produced by the throttled engine each time the back-pressure mechanism asks for a new "unit" (a new DataBuffer)
	//
	// The value established here is nullable (and null by default) because it will work as an override of the
	// value established at the ThymeleafReactiveViewResolver for the same purpose.
	private Integer responseMaxChunkSizeBytes = null;






	public ThymeleafReactiveView() {
		super();
	}



	public String getMarkupSelector() {
		return (this.markupSelectors == null || this.markupSelectors.size() == 0? null : this.markupSelectors.iterator().next());
	}


	public void setMarkupSelector(final String markupSelector) {
		this.markupSelectors =
				(markupSelector == null || markupSelector.trim().length() == 0? null : Collections.singleton(markupSelector.trim()));
	}



	// This flag is used from the ViewResolver in order to determine if it has to push its own
	// configuration to the View (which it will do until the View has been specifically configured).
	boolean isDefaultCharsetSet() {
		return this.defaultCharsetSet;
	}


	// Implemented at AbstractView, but overridden here in order to set the flag
	@Override
	public void setDefaultCharset(final Charset defaultCharset) {
		super.setDefaultCharset(defaultCharset);
		this.defaultCharsetSet = true;
	}




	// This flag is used from the ViewResolver in order to determine if it has to push its own
	// configuration to the View (which it will do until the View has been specifically configured).
	boolean isSupportedMediaTypesSet() {
		return this.supportedMediaTypesSet;
	}


	// Implemented at AbstractView, but overridden here in order to set the flag
	@Override
	public void setSupportedMediaTypes(final List<MediaType> supportedMediaTypes) {
		super.setSupportedMediaTypes(supportedMediaTypes);
		this.supportedMediaTypesSet = true;
	}




	public String getBeanName() {
		return this.beanName;
	}


	public void setBeanName(final String beanName) {
		this.beanName = beanName;
	}




	public String getTemplateName() {
		return this.templateName;
	}


	public void setTemplateName(final String templateName) {
		this.templateName = templateName;
	}




	protected Locale getLocale() {
		return this.locale;
	}


	protected void setLocale(final Locale locale) {
		this.locale = locale;

	}




	// Default is Integer.MAX_VALUE, which means no explicit limit (note there can still be a limit in
	// the size of the chunks if execution is data driven, as output will be sent to the server after
	// the processing of each data-driver buffer).
	public int getResponseMaxChunkSizeBytes() {
		return this.responseMaxChunkSizeBytes == null?
				DEFAULT_RESPONSE_CHUNK_SIZE_BYTES : this.responseMaxChunkSizeBytes.intValue();
	}


	// We need this one at the ViewResolver to determine if a value has been set at all
	Integer getNullableResponseMaxChunkSize() {
		return this.responseMaxChunkSizeBytes;
	}


	public void setResponseMaxChunkSizeBytes(final int responseMaxBufferSizeBytes) {
		this.responseMaxChunkSizeBytes = Integer.valueOf(responseMaxBufferSizeBytes);
	}




	protected ISpringWebFluxTemplateEngine getTemplateEngine() {
		return this.templateEngine;
	}


	protected void setTemplateEngine(final ISpringWebFluxTemplateEngine templateEngine) {
		this.templateEngine = templateEngine;
	}




	public Map<String,Object> getStaticVariables() {
		if (this.staticVariables == null) {
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(this.staticVariables);
	}


	public void addStaticVariable(final String name, final Object value) {
		if (this.staticVariables == null) {
			this.staticVariables = new HashMap<String, Object>(3, 1.0f);
		}
		this.staticVariables.put(name, value);
	}


	public void setStaticVariables(final Map<String, ?> variables) {
		if (variables != null) {
			if (this.staticVariables == null) {
				this.staticVariables = new HashMap<String, Object>(3, 1.0f);
			}
			this.staticVariables.putAll(variables);
		}
	}






	@Override
	protected Mono<Void> renderInternal(
			final Map<String, Object> renderAttributes, final MediaType contentType, final ServerWebExchange exchange) {
		return renderFragmentInternal(this.markupSelectors, renderAttributes, contentType, exchange);
	}


	protected Mono<Void> renderFragmentInternal(
			final Set<String> markupSelectorsToRender, final Map<String, Object> renderAttributes,
			final MediaType contentType, final ServerWebExchange exchange) {

		final String viewTemplateName = getTemplateName();
		final ISpringWebFluxTemplateEngine viewTemplateEngine = getTemplateEngine();

		if (viewTemplateName == null) {
			return Mono.error(new IllegalArgumentException("Property 'templateName' is required"));
		}
		if (getLocale() == null) {
			return Mono.error(new IllegalArgumentException("Property 'locale' is required"));
		}
		if (viewTemplateEngine == null) {
			return Mono.error(new IllegalArgumentException("Property 'thymeleafTemplateEngine' is required"));
		}

		final ServerHttpResponse response = exchange.getResponse();

        /*
         * ----------------------------------------------------------------------------------------------------------
         * GATHERING OF THE MERGED MODEL
         * ----------------------------------------------------------------------------------------------------------
         * - The merged model is the map that will be used for initialising the Thymelef IContext. This context will
         *   contain all the data accessible by the template during its execution.
         * - The base of the merged model is the ModelMap created by the Controller, but there are some additional
         *   things
         * ----------------------------------------------------------------------------------------------------------
         */

		final Map<String, Object> mergedModel = new HashMap<>(30);
		// First of all, set all the static variables into the mergedModel
		final Map<String, Object> templateStaticVariables = getStaticVariables();
		if (templateStaticVariables != null) {
			mergedModel.putAll(templateStaticVariables);
		}
		// Add path variables to merged model (if there are any)
		final Map<String, Object> pathVars =
				(Map<String, Object>) exchange.getAttributes().get(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		if (pathVars != null) {
			mergedModel.putAll(pathVars);
		}
		// Simply dump all the renderAttributes (model coming from the controller) into the merged model
		if (renderAttributes != null) {
			mergedModel.putAll(renderAttributes);
		}

		final ApplicationContext applicationContext = getApplicationContext();

		// Initialize RequestContext (reactive version) and add it to the model as another attribute,
		// so that it can be retrieved from elsewhere.
		final RequestContext requestContext = new RequestContext(exchange, mergedModel, applicationContext);
		final SpringWebFluxThymeleafRequestContext thymeleafRequestContext =
				new SpringWebFluxThymeleafRequestContext(requestContext, exchange);

		mergedModel.put(SpringContextVariableNames.SPRING_REQUEST_CONTEXT, requestContext);
		// Add the Thymeleaf RequestContext wrapper that we will be using in this dialect (the bare RequestContext
		// stays in the context to for compatibility with other dialects)
		mergedModel.put(SpringContextVariableNames.THYMELEAF_REQUEST_CONTEXT, thymeleafRequestContext);


		// Expose Thymeleaf's own evaluation context as a model variable
		//
		// Note Spring's EvaluationContexts are NOT THREAD-SAFE (in exchange for SpelExpressions being thread-safe).
		// That's why we need to create a new EvaluationContext for each request / template execution, even if it is
		// quite expensive to create because of requiring the initialization of several ConcurrentHashMaps.
		final ConversionService conversionService =
				applicationContext.getBeanNamesForType(ConversionService.class).length > 0?
						applicationContext.getBean(ConversionService.class): null;
		final ThymeleafEvaluationContext evaluationContext =
				new ThymeleafEvaluationContext(applicationContext, conversionService);
		mergedModel.put(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME, evaluationContext);


		// Initialize those model attributes that might be instances of ReactiveLazyContextVariable and therefore
		// need to be set the ReactiveAdapterRegistry.
		initializeApplicationAwareModel(applicationContext, mergedModel);


        /*
         * ----------------------------------------------------------------------------------------------------------
         * INSTANTIATION OF THE CONTEXT
         * ----------------------------------------------------------------------------------------------------------
         * - Once the model has been merged, we can create the Thymeleaf context object itself.
         * - The reason it is an ExpressionContext and not a Context is that before executing the template itself,
         *   we might need to use it for computing the markup selectors (if "template :: selector" was specified).
         * - The reason it is not a WebExpressionContext is that this class is linked to the Servlet API, which
         *   might not be present in a Spring WebFlux environment.
         * ----------------------------------------------------------------------------------------------------------
         */

		final IEngineConfiguration configuration = viewTemplateEngine.getConfiguration();
		final SpringWebFluxExpressionContext context =
				new SpringWebFluxExpressionContext(configuration, exchange, getLocale(), mergedModel);


        /*
         * ----------------------------------------------------------------------------------------------------------
         * COMPUTATION OF (OPTIONAL) MARKUP SELECTORS
         * ----------------------------------------------------------------------------------------------------------
         * - If view name has been specified with a template selector (in order to execute only a fragment of
         *   the template) like "template :: selector", we will extract it and compute it.
         * ----------------------------------------------------------------------------------------------------------
         */

		final String templateName;
		final Set<String> markupSelectors;
		if (!viewTemplateName.contains("::")) {
			// No fragment specified at the template name

			templateName = viewTemplateName;
			markupSelectors = null;

		} else {
			// Template name contains a fragment name, so we should parse it as such

			final IStandardExpressionParser parser = StandardExpressions.getExpressionParser(configuration);

			final FragmentExpression fragmentExpression;
			try {
				// By parsing it as a standard expression, we might profit from the expression cache
				fragmentExpression = (FragmentExpression) parser.parseExpression(context, "~{" + viewTemplateName + "}");
			} catch (final TemplateProcessingException e) {
				return Mono.error(
						new IllegalArgumentException("Invalid template name specification: '" + viewTemplateName + "'"));
			}

			final FragmentExpression.ExecutedFragmentExpression fragment =
					FragmentExpression.createExecutedFragmentExpression(context, fragmentExpression, StandardExpressionExecutionContext.NORMAL);

			templateName = FragmentExpression.resolveTemplateName(fragment);
			markupSelectors = FragmentExpression.resolveFragments(fragment);
			final Map<String,Object> nameFragmentParameters = fragment.getFragmentParameters();

			if (nameFragmentParameters != null) {

				if (fragment.hasSyntheticParameters()) {
					// We cannot allow synthetic parameters because there is no way to specify them at the template
					// engine execution!
					return Mono.error(new IllegalArgumentException(
							"Parameters in a view specification must be named (non-synthetic): '" + viewTemplateName + "'"));
				}

				context.setVariables(nameFragmentParameters);

			}

		}

		final Set<String> processMarkupSelectors;
		if (markupSelectors != null && markupSelectors.size() > 0) {
			if (markupSelectorsToRender != null && markupSelectorsToRender.size() > 0) {
				return Mono.error(new IllegalArgumentException(
						"A markup selector has been specified (" + Arrays.asList(markupSelectors) + ") for a view " +
								"that was already being executed as a fragment (" + Arrays.asList(markupSelectorsToRender) + "). " +
								"Only one fragment selection is allowed."));
			}
			processMarkupSelectors = markupSelectors;
		} else {
			if (markupSelectorsToRender != null && markupSelectorsToRender.size() > 0) {
				processMarkupSelectors = markupSelectorsToRender;
			} else {
				processMarkupSelectors = null;
			}
		}


        /*
         * ----------------------------------------------------------------------------------------------------------
         * COMPUTATION OF TEMPLATE PROCESSING PARAMETERS AND HTTP HEADERS
         * ----------------------------------------------------------------------------------------------------------
         * - At this point we will compute the final values of the different parameters needed for processing the
         *   template (locale, encoding, buffer sizes, etc.)
         * ----------------------------------------------------------------------------------------------------------
         */

		final int templateResponseMaxChunkSizeBytes = getResponseMaxChunkSizeBytes();

		final HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
		final Locale templateLocale = getLocale();
		if (templateLocale != null) {
			responseHeaders.setContentLanguage(templateLocale);
		}

		// Get the charset from the selected content type (or use default)
		final Charset charset = getCharset(contentType).orElse(getDefaultCharset());


        /*
         * ----------------------------------------------------------------------------------------------------------
         * SET (AND RETURN) THE TEMPLATE PROCESSING Flux<DataBuffer> OBJECTS
         * ----------------------------------------------------------------------------------------------------------
         * - There are three possible processing mode, for each of which a Publisher<DataBuffer> will be created in a
         *   different way:
         *
         *     1. FULL: Output buffers not limited (templateResponseMaxChunkSizeBytes == Integer.MAX_VALUE) and
         *        no data-driven execution (no context variable of type Publisher<X> driving the template engine
         *        execution): In this case Thymeleaf will be executed unthrottled, in full mode, writing output
         *        to a single DataBuffer instanced before execution, and which will be passed to the output channels
         *        in a single onNext(buffer) call (immediately followed by onComplete()).
         *
         *     2. CHUNKED: Output buffers limited in size (responseMaxChunkSizeBytes) but no data-driven
         *        execution (no Publisher<X> driving engine execution). All model attributes are expected to be fully
         *        resolved before engine execution (except those implementing Thymeleaf's ILazyContextVariable
         *        interface, including its reactive implementation ReactiveLazyContextVariable) and the Thymeleaf
         *        engine will execute in throttled mode, performing a full-stop each time the buffer reaches the
         *        specified size, sending it to the output channels with onNext(buffer) and then waiting until
         *        these output channels make the engine resume its work with a new request(n) call. This
         *        execution mode will request an output flush from the server after producing each buffer.
         *
         *     3. DATA-DRIVEN: one of the model attributes is a Publisher<X> wrapped inside an implementation
         *        of the IReactiveDataDriverContextVariable<?> interface. In this case, the Thymeleaf engine will
         *        execute as a response to onNext(List<X>) events triggered by this Publisher. The
         *        "bufferSizeElements" specified at the model attribute will define the amount of elements
         *        produced by this Publisher that will be buffered into a List<X> before triggering the template
         *        engine each time (which is why Thymeleaf will react on onNext(List<X>) and not onNext(X)). Thymeleaf
         *        will expect to find a "th:each" iteration on the data-driven variable inside the processed template,
         *        and will be executed in throttled mode for the published elements, sending the resulting DataBuffer
         *        (or DataBuffers) to the output channels via onNext(buffer) and stopping until a new onNext(List<X>)
         *        event is triggered. When execution is data-driven, a limit in size can be optionally specified for
         *        the output buffers (responseMaxChunkSizeBytes) which will make Thymeleaf never send
         *        to the output channels a buffer bigger than that (thus splitting the output generated for a List<X>
         *        of published elements into several buffers if required) and also will make Thymeleaf request
         *        an output flush from the server after producing each buffer.
         * ----------------------------------------------------------------------------------------------------------
         */


		final Publisher<DataBuffer> stream =
				viewTemplateEngine.processStream(
						templateName, processMarkupSelectors, context, response.bufferFactory(), contentType, charset,
						templateResponseMaxChunkSizeBytes); // FULL/DATADRIVEN if MAX_VALUE, CHUNKED/DATADRIVEN if other

		if (templateResponseMaxChunkSizeBytes == Integer.MAX_VALUE) {

			// No size limit for output chunks has been set, so we will let the
			// server apply its standard behaviour ("writeWith").
			return response.writeWith(stream);

		}

		// A limit for output chunks has been set, so we will use "writeAndFlushWith" in order to make
		// sure that output is flushed after each buffer.
		return response.writeAndFlushWith(Flux.from(stream).window(1));

	}




	private static Optional<Charset> getCharset(final MediaType mediaType) {
		return mediaType != null ? Optional.ofNullable(mediaType.getCharset()) : Optional.empty();
	}




	private static void initializeApplicationAwareModel(
			final ApplicationContext applicationContext, final Map<String,Object> model) {

		final ReactiveAdapterRegistry reactiveAdapterRegistry;
		try {
			reactiveAdapterRegistry = applicationContext.getBean(ReactiveAdapterRegistry.class);
		} catch (final NoSuchBeanDefinitionException ignored) {
			// No registry, but note that we can live without it (though limited to Flux and Mono)
			return;
		}

		for (final Object value : model.values()) {
			if (value instanceof ReactiveLazyContextVariable) {
				((ReactiveLazyContextVariable)value).setReactiveAdapterRegistry(reactiveAdapterRegistry);
			} else if (value instanceof ReactiveDataDriverContextVariable) {
				((ReactiveDataDriverContextVariable)value).setReactiveAdapterRegistry(reactiveAdapterRegistry);
			}
		}

	}




}