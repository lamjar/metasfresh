package de.metas.camel.shipping.shipment;

import de.metas.camel.shipping.RouteBuilderCommonUtil;
import de.metas.common.shipment.JsonCreateShipmentRequest;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.builder.endpoint.dsl.HttpEndpointBuilderFactory;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.model.dataformat.JacksonXMLDataFormat;

import static de.metas.camel.shipping.shipment.SiroShipmentConstants.AUTHORIZATION;
import static de.metas.camel.shipping.shipment.SiroShipmentConstants.AUTHORIZATION_TOKEN;
import static de.metas.camel.shipping.shipment.SiroShipmentConstants.CREATE_SHIPMENT_MF_URL;
import static de.metas.camel.shipping.shipment.SiroShipmentConstants.SIRO_FTP_PATH;

public class ShipmentXmlToJsonRouteBuilder extends EndpointRouteBuilder
{

	private static final String MF_SHIPMENT_FILEMAKER_XML_TO_JSON = "MF-FM-To-Json-Shipment";

	@Override public void configure() throws Exception
	{
		errorHandler(defaultErrorHandler());

		RouteBuilderCommonUtil.setupProperties(getContext());

		final JacksonDataFormat jacksonDataFormat = RouteBuilderCommonUtil.setupMetasfreshJSONFormat(getContext(), JsonCreateShipmentRequest.class);
		final JacksonXMLDataFormat jacksonXMLDataFormat = RouteBuilderCommonUtil.setupFileMakerFormat(getContext());

		from(SIRO_FTP_PATH)
				.routeId(MF_SHIPMENT_FILEMAKER_XML_TO_JSON)
				.streamCaching()
				.unmarshal(jacksonXMLDataFormat)
				.process(new ShipmentXmlToJsonProcessor())
				.choice()
				    .when(header(RouteBuilderCommonUtil.NUMBER_OF_ITEMS).isLessThanOrEqualTo(0))
						.log(LoggingLevel.INFO, "Nothing to do! no shipments were found in file:" + header(Exchange.FILE_NAME))
					.otherwise()
				   		.log(LoggingLevel.INFO, "Posting " + header(RouteBuilderCommonUtil.NUMBER_OF_ITEMS) + " shipments to metasfresh.")
				        .marshal(jacksonDataFormat)
						.setHeader(AUTHORIZATION, simple(AUTHORIZATION_TOKEN))
						.setHeader(Exchange.HTTP_METHOD, constant(HttpEndpointBuilderFactory.HttpMethods.POST))
				 		.to(http(CREATE_SHIPMENT_MF_URL))
				.end()
		;
	}
}
