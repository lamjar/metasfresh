package de.metas.customs;

import static org.adempiere.model.InterfaceWrapperHelper.newInstance;
import static org.adempiere.model.InterfaceWrapperHelper.save;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.adempiere.test.AdempiereTestHelper;
import org.compiere.model.I_C_BPartner_Location;
import org.compiere.model.I_C_DocType;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_PaymentTerm;
import org.compiere.model.I_C_UOM;
import org.compiere.model.I_M_Warehouse;
import org.compiere.util.TimeUtil;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;

import de.metas.adempiere.model.I_AD_User;
import de.metas.adempiere.model.I_M_Product;
import de.metas.bpartner.BPartnerId;
import de.metas.bpartner.BPartnerLocationId;
import de.metas.currency.CurrencyCode;
import de.metas.currency.ICurrencyDAO;
import de.metas.currency.impl.PlainCurrencyDAO;
import de.metas.customs.process.ShipmentLinesForCustomsInvoiceRepo;
import de.metas.document.DocTypeId;
import de.metas.document.engine.DocStatus;
import de.metas.document.engine.IDocument;
import de.metas.inout.InOutAndLineId;
import de.metas.inout.InOutId;
import de.metas.inout.model.I_M_InOut;
import de.metas.inout.model.I_M_InOutLine;
import de.metas.interfaces.I_C_BPartner;
import de.metas.interfaces.I_C_OrderLine;
import de.metas.money.CurrencyId;
import de.metas.money.Money;
import de.metas.order.OrderId;
import de.metas.order.OrderLineRepository;
import de.metas.product.ProductId;
import de.metas.quantity.Quantity;
import de.metas.quantity.StockQtyAndUOMQty;
import de.metas.quantity.StockQtyAndUOMQtys;
import de.metas.uom.CreateUOMConversionRequest;
import de.metas.uom.IUOMConversionDAO;
import de.metas.uom.UOMConstants;
import de.metas.uom.UomId;
import de.metas.user.UserId;
import de.metas.util.Services;
import de.metas.util.time.FixedTimeSource;
import de.metas.util.time.SystemTime;
import lombok.NonNull;

/*
 * #%L
 * de.metas.business
 * %%
 * Copyright (C) 2019 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public class CustomsInvoiceServiceTest
{
	private CustomsInvoiceService service;

	private BPartnerLocationId logisticCompany;
	private UserId logisticUserId;

	private CurrencyId chf;

	private CurrencyId euro;

	private BigDecimal currencyMultiplier;

	private DocTypeId docTypeId;

	private String documentNo;

	private LocalDate invoiceDate;

	private I_C_UOM uom1;

	private I_C_UOM uom2;

	private BigDecimal convertionMultiplier;
	private BigDecimal conversionDivisor;

	private ProductId product1;

	@Before
	public void init()
	{
		AdempiereTestHelper.get().init();

		final CustomsInvoiceRepository customsInvoiceRepo = new CustomsInvoiceRepository();
		final OrderLineRepository orderLineRepo = new OrderLineRepository();
		final ShipmentLinesForCustomsInvoiceRepo shipmentLinesForCustomsInvoiceRepo = new ShipmentLinesForCustomsInvoiceRepo();
		service = new CustomsInvoiceService(customsInvoiceRepo, orderLineRepo, shipmentLinesForCustomsInvoiceRepo);

		SystemTime.setTimeSource(new FixedTimeSource(2019, 5, 22, 11, 21, 13));

		logisticCompany = createBPartnerAndLocation("LogisticCompany", "Logistic Company Address");
		logisticUserId = createUser(logisticCompany.getBpartnerId(), "Logistic company user");

		chf = PlainCurrencyDAO.createCurrencyId(CurrencyCode.CHF);
		euro = PlainCurrencyDAO.createCurrencyId(CurrencyCode.EUR);

		currencyMultiplier = BigDecimal.valueOf(1.13);

		docTypeId = createDocType("Customs Invoice DocType");
		documentNo = "12345";

		invoiceDate = SystemTime.asLocalDate();

		uom1 = createUOM("UomCode1");
		uom2 = createUOM("UomCode2");
		createUOM(UOMConstants.X12_KILOGRAM);

		convertionMultiplier = BigDecimal.valueOf(2);
		conversionDivisor = BigDecimal.valueOf(0.5);

		product1 = createProduct("Product1", uom1);

		createUOMConversion(product1, uom2, uom1, convertionMultiplier, conversionDivisor);

		// createCurrencyConversion(euro, chf, currencyMultiplier, currencyDivisor);
		// createCurrencyConversion(chf, euro, currencyDivisor, currencyMultiplier);

		final PlainCurrencyDAO currencyDAO = (PlainCurrencyDAO)Services.get(ICurrencyDAO.class);
		currencyDAO.setRate(euro, chf, currencyMultiplier);

	}

	@Test
	public void createCustomsInvoice_oneShipmentLine()
	{
		final Money priceActual = Money.of(BigDecimal.TEN, chf);

		final StockQtyAndUOMQty orderLineQty = StockQtyAndUOMQtys.createConvert(BigDecimal.valueOf(2), product1, UomId.ofRepoId(uom1.getC_UOM_ID()));

		final BPartnerLocationId bpartnerAndLocation2 = createBPartnerAndLocation("Partner2", "address2");

		final OrderId order = createOrder(bpartnerAndLocation2);
		final I_C_OrderLine orderLine1 = createOrderLine(order, orderLineQty, priceActual);

		final InOutId inout1 = createShipment(bpartnerAndLocation2);
		final I_M_InOutLine shipmentLineRecord1 = createInOutLine(inout1, orderLine1);

		final InOutAndLineId shipmentLine1 = InOutAndLineId.ofRepoId(inout1.getRepoId(), shipmentLineRecord1.getM_InOutLine_ID());

		ImmutableSetMultimap<ProductId, InOutAndLineId> linesToExportMap = ImmutableSetMultimap.<ProductId, InOutAndLineId> builder()
				.put(product1, shipmentLine1)
				.build();

		final String bpartnerAddress = "Bpartner Address";

		final CustomsInvoiceRequest customsInvoiceRequest = CustomsInvoiceRequest.builder()
				.bpartnerAndLocationId(logisticCompany)
				.bpartnerAddress(bpartnerAddress)
				.currencyId(chf)
				.docTypeId(docTypeId)
				.documentNo(documentNo)
				.invoiceDate(invoiceDate)
				.userId(logisticUserId)
				.linesToExportMap(linesToExportMap)
				.build();

		// invoke the method under test
		final CustomsInvoice customsInvoice = service.generateCustomsInvoice(customsInvoiceRequest);

		assertNotNull(customsInvoice);
		assertNotNull(customsInvoice.getId());

		assertThat(customsInvoice.getBpartnerAndLocationId(), is(logisticCompany));
		assertThat(customsInvoice.getCurrencyId(), is(chf));

		assertThat(customsInvoice.getDocTypeId(), is(docTypeId));
		assertThat(customsInvoice.getInvoiceDate(), is(invoiceDate));
		assertThat(customsInvoice.getUserId(), is(logisticUserId));

		assertThat(DocStatus.Drafted, is(customsInvoice.getDocStatus()));

		final ImmutableList<CustomsInvoiceLine> lines = customsInvoice.getLines();

		assertNotNull(lines);
		assertThat(lines.size(), comparesEqualTo(1));

		final CustomsInvoiceLine customsInvoiceLine = lines.get(0);

		assertNotNull(customsInvoiceLine.getId());

		final Money expectedLineNetAmt = Money.of(priceActual.toBigDecimal().multiply(orderLineQty.getUOMQtyNotNull().toBigDecimal()), chf);

		assertThat(customsInvoiceLine.getLineNetAmt(), is(expectedLineNetAmt));
		assertThat(customsInvoiceLine.getLineNo(), is(10));
		assertThat(customsInvoiceLine.getProductId(), is(product1));
		assertThat(customsInvoiceLine.getQuantity(), is(orderLineQty.getUOMQtyNotNull()));
		assertThat(customsInvoiceLine.getQuantity().getUomId(), is(UomId.ofRepoId(uom1.getC_UOM_ID())));
	}

	@Test
	public void createCustomsInvoice_twoShipmentLines()
	{
		final BPartnerLocationId bpartnerAndLocation2 = createBPartnerAndLocation("Partner2", "address2");
		final OrderId order = createOrder(bpartnerAndLocation2);

		final Money priceActual1 = Money.of(BigDecimal.TEN, chf);

		final StockQtyAndUOMQty orderLineQty1 = StockQtyAndUOMQtys.createConvert(BigDecimal.valueOf(2), product1, UomId.ofRepoId(uom1.getC_UOM_ID()));
		final I_C_OrderLine orderLine1 = createOrderLine(order, orderLineQty1, priceActual1);

		final InOutId inout1 = createShipment(bpartnerAndLocation2);

		final I_M_InOutLine shipmentLineRecord1 = createInOutLine(inout1, orderLine1);

		final InOutAndLineId shipmentLine1 = InOutAndLineId.ofRepoId(inout1.getRepoId(), shipmentLineRecord1.getM_InOutLine_ID());

		final Money priceActual2 = Money.of(BigDecimal.valueOf(20), chf);

		final StockQtyAndUOMQty orderLineQty2 = StockQtyAndUOMQtys.createConvert(BigDecimal.valueOf(5), product1, UomId.ofRepoId(uom1.getC_UOM_ID()));
		final I_C_OrderLine orderLine2 = createOrderLine(order, orderLineQty2, priceActual2);

		final I_M_InOutLine shipmentLineRecord2 = createInOutLine(inout1, orderLine2);

		final InOutAndLineId shipmentLine2 = InOutAndLineId.ofRepoId(inout1.getRepoId(), shipmentLineRecord2.getM_InOutLine_ID());

		SetMultimap<ProductId, InOutAndLineId> linesToExportMap = ImmutableSetMultimap.<ProductId, InOutAndLineId> builder()
				.put(product1, shipmentLine1)
				.put(product1, shipmentLine2)
				.build();

		final String bpartnerAddress = "Bpartner Address";

		final CustomsInvoiceRequest customsInvoiceRequest = CustomsInvoiceRequest.builder()
				.bpartnerAndLocationId(logisticCompany)
				.bpartnerAddress(bpartnerAddress)
				.currencyId(chf)
				.docTypeId(docTypeId)
				.documentNo(documentNo)
				.invoiceDate(invoiceDate)
				.userId(logisticUserId)
				.linesToExportMap(linesToExportMap)
				.build();

		final CustomsInvoice customsInvoice = service.generateCustomsInvoice(customsInvoiceRequest);

		assertNotNull(customsInvoice);
		assertNotNull(customsInvoice.getId());

		assertThat(customsInvoice.getBpartnerAndLocationId(), is(logisticCompany));
		assertThat(customsInvoice.getCurrencyId(), is(chf));

		assertThat(customsInvoice.getDocTypeId(), is(docTypeId));
		assertThat(customsInvoice.getInvoiceDate(), is(invoiceDate));
		assertThat(customsInvoice.getUserId(), is(logisticUserId));

		assertThat(DocStatus.Drafted, is(customsInvoice.getDocStatus()));

		final ImmutableList<CustomsInvoiceLine> lines = customsInvoice.getLines();

		assertNotNull(lines);
		assertThat(lines.size(), comparesEqualTo(1));

		final CustomsInvoiceLine customsInvoiceLine = lines.get(0);

		assertNotNull(customsInvoiceLine.getId());

		final BigDecimal expectedPrice = (priceActual1.toBigDecimal().multiply(orderLineQty1.getUOMQtyNotNull().toBigDecimal()))
				.add(priceActual2.toBigDecimal().multiply(orderLineQty2.getUOMQtyNotNull().toBigDecimal()));
		final Money expectedLineNetAmt = Money.of(expectedPrice, chf);

		assertThat(customsInvoiceLine.getLineNetAmt(), is(expectedLineNetAmt));
		assertThat(customsInvoiceLine.getLineNo(), is(10));
		assertThat(customsInvoiceLine.getProductId(), is(product1));

		final Quantity expectedQty = orderLineQty1.getUOMQtyNotNull().add(orderLineQty2.getUOMQtyNotNull());
		assertThat(customsInvoiceLine.getQuantity(), is(expectedQty));
		assertThat(customsInvoiceLine.getQuantity().getUomId(), is(UomId.ofRepoId(uom1.getC_UOM_ID())));

	}

	@Test
	public void createCustomsInvoice_twoShipmentLines_DifferentUOM()
	{
		final BPartnerLocationId bpartnerAndLocation2 = createBPartnerAndLocation("Partner2", "address2");
		final OrderId order = createOrder(bpartnerAndLocation2);

		final Money priceActual1 = Money.of(BigDecimal.TEN, chf);

		final StockQtyAndUOMQty orderLineQty1 = StockQtyAndUOMQtys.createConvert(BigDecimal.valueOf(2), product1, UomId.ofRepoId(uom1.getC_UOM_ID()));
		final I_C_OrderLine orderLine1 = createOrderLine(order, orderLineQty1, priceActual1);

		final InOutId inout1 = createShipment(bpartnerAndLocation2);

		final I_M_InOutLine shipmentLineRecord1 = createInOutLine(inout1, orderLine1);

		final InOutAndLineId shipmentLine1 = InOutAndLineId.ofRepoId(inout1.getRepoId(), shipmentLineRecord1.getM_InOutLine_ID());

		final Money priceActual2 = Money.of(BigDecimal.valueOf(20), chf);

		final StockQtyAndUOMQty orderLineQty2 = StockQtyAndUOMQtys.createConvert(BigDecimal.valueOf(5), product1, UomId.ofRepoId(uom2.getC_UOM_ID())); // => product1 is in UOM1, so uomQty=2.5
		final I_C_OrderLine orderLine2 = createOrderLine(order, orderLineQty2, priceActual2);

		final I_M_InOutLine shipmentLineRecord2 = createInOutLine(inout1, orderLine2);

		final InOutAndLineId shipmentLine2 = InOutAndLineId.ofRepoId(inout1.getRepoId(), shipmentLineRecord2.getM_InOutLine_ID());

		SetMultimap<ProductId, InOutAndLineId> linesToExportMap = ImmutableSetMultimap.<ProductId, InOutAndLineId> builder()
				.put(product1, shipmentLine1)
				.put(product1, shipmentLine2)
				.build();

		final String bpartnerAddress = "Bpartner Address";

		final CustomsInvoiceRequest customsInvoiceRequest = CustomsInvoiceRequest.builder()
				.bpartnerAndLocationId(logisticCompany)
				.bpartnerAddress(bpartnerAddress)
				.currencyId(chf)
				.docTypeId(docTypeId)
				.documentNo(documentNo)
				.invoiceDate(invoiceDate)
				.userId(logisticUserId)
				.linesToExportMap(linesToExportMap)
				.build();

		final CustomsInvoice customsInvoice = service.generateCustomsInvoice(customsInvoiceRequest);

		assertNotNull(customsInvoice);
		assertNotNull(customsInvoice.getId());

		assertThat(customsInvoice.getBpartnerAndLocationId(), is(logisticCompany));
		assertThat(customsInvoice.getCurrencyId(), is(chf));

		assertThat(customsInvoice.getDocTypeId(), is(docTypeId));
		assertThat(customsInvoice.getInvoiceDate(), is(invoiceDate));
		assertThat(customsInvoice.getUserId(), is(logisticUserId));

		assertThat(DocStatus.Drafted, is(customsInvoice.getDocStatus()));

		final ImmutableList<CustomsInvoiceLine> lines = customsInvoice.getLines();

		assertNotNull(lines);
		assertThat(lines.size(), comparesEqualTo(1));

		final CustomsInvoiceLine customsInvoiceLine = lines.get(0);

		assertNotNull(customsInvoiceLine.getId());

		final BigDecimal qty2inUom1 = orderLineQty2.getUOMQtyNotNull().toBigDecimal().multiply(convertionMultiplier);
		final Quantity expectedQty = orderLineQty1.getUOMQtyNotNull().add(qty2inUom1);
		assertThat(customsInvoiceLine.getQuantity(), is(expectedQty));

		final BigDecimal expectedPrice = priceActual1.toBigDecimal().multiply(orderLineQty1.getUOMQtyNotNull().toBigDecimal()) // 2 UOM1 x 10 CHF = 20 CHF
				.add(priceActual2.toBigDecimal().multiply(orderLineQty2.getUOMQtyNotNull().toBigDecimal())); // 2.5 UOM2 x 20 CHF = 50 CHF

		final Money expectedLineNetAmt = Money.of(expectedPrice, chf);

		assertThat(customsInvoiceLine.getLineNetAmt(), is(expectedLineNetAmt));
		assertThat(customsInvoiceLine.getLineNo(), is(10));
		assertThat(customsInvoiceLine.getProductId(), is(product1));

		assertThat(customsInvoiceLine.getQuantity().getUomId(), is(UomId.ofRepoId(uom1.getC_UOM_ID())));
	}

	@Test
	public void createCustomsInvoice_twoShipmentLines_DifferentUOM_DifferentCurrency()
	{
		final BPartnerLocationId bpartnerAndLocation2 = createBPartnerAndLocation("Partner2", "address2");
		final OrderId order = createOrder(bpartnerAndLocation2);

		final Money priceActual1 = Money.of(BigDecimal.TEN, chf);

		final StockQtyAndUOMQty orderLineQty1 = StockQtyAndUOMQtys.createConvert(BigDecimal.valueOf(2), product1, UomId.ofRepoId(uom1.getC_UOM_ID()));
		final I_C_OrderLine orderLine1 = createOrderLine(order, orderLineQty1, priceActual1);
		final InOutId inout1 = createShipment(bpartnerAndLocation2);

		final I_M_InOutLine shipmentLineRecord1 = createInOutLine(inout1, orderLine1);

		final InOutAndLineId shipmentLine1 = InOutAndLineId.ofRepoId(inout1.getRepoId(), shipmentLineRecord1.getM_InOutLine_ID());

		final Money priceActual2 = Money.of(BigDecimal.valueOf(20), euro);

		final StockQtyAndUOMQty orderLineQty2 = StockQtyAndUOMQtys.createConvert(BigDecimal.valueOf(5), product1, UomId.ofRepoId(uom2.getC_UOM_ID()));
		final I_C_OrderLine orderLine2 = createOrderLine(order, orderLineQty2, priceActual2);

		final I_M_InOutLine shipmentLineRecord2 = createInOutLine(inout1, orderLine2);

		final InOutAndLineId shipmentLine2 = InOutAndLineId.ofRepoId(inout1.getRepoId(), shipmentLineRecord2.getM_InOutLine_ID());

		SetMultimap<ProductId, InOutAndLineId> linesToExportMap = ImmutableSetMultimap.<ProductId, InOutAndLineId> builder()
				.put(product1, shipmentLine1)
				.put(product1, shipmentLine2)
				.build();

		final String bpartnerAddress = "Bpartner Address";

		final CustomsInvoiceRequest customsInvoiceRequest = CustomsInvoiceRequest.builder()
				.bpartnerAndLocationId(logisticCompany)
				.bpartnerAddress(bpartnerAddress)
				.currencyId(chf)
				.docTypeId(docTypeId)
				.documentNo(documentNo)
				.invoiceDate(invoiceDate)
				.userId(logisticUserId)
				.linesToExportMap(linesToExportMap)
				.build();

		final CustomsInvoice customsInvoice = service.generateCustomsInvoice(customsInvoiceRequest);

		assertNotNull(customsInvoice);
		assertNotNull(customsInvoice.getId());

		assertThat(customsInvoice.getBpartnerAndLocationId(), is(logisticCompany));
		assertThat(customsInvoice.getCurrencyId(), is(chf));

		assertThat(customsInvoice.getDocTypeId(), is(docTypeId));
		assertThat(customsInvoice.getInvoiceDate(), is(invoiceDate));
		assertThat(customsInvoice.getUserId(), is(logisticUserId));

		assertThat(DocStatus.Drafted, is(customsInvoice.getDocStatus()));

		final ImmutableList<CustomsInvoiceLine> lines = customsInvoice.getLines();

		assertNotNull(lines);
		assertThat(lines.size(), comparesEqualTo(1));

		final CustomsInvoiceLine customsInvoiceLine = lines.get(0);

		assertNotNull(customsInvoiceLine.getId());

		final BigDecimal qty2inUom1 = orderLineQty2.getUOMQtyNotNull().toBigDecimal().multiply(convertionMultiplier);
		final Quantity expectedQty = orderLineQty1.getUOMQtyNotNull().add(qty2inUom1);
		assertThat(customsInvoiceLine.getQuantity(), is(expectedQty));

		final BigDecimal price2inCurrency1 = priceActual2.toBigDecimal().multiply(currencyMultiplier);

		final BigDecimal expectedPrice = (priceActual1.toBigDecimal()
				.multiply(orderLineQty1.getUOMQtyNotNull().toBigDecimal()))
						.add(price2inCurrency1.multiply(orderLineQty2.getUOMQtyNotNull().toBigDecimal()));

		final Money expectedLineNetAmt = Money.of(expectedPrice, chf);

		assertThat(customsInvoiceLine.getLineNo(), is(10));
		assertThat(customsInvoiceLine.getProductId(), is(product1));
		assertThat(customsInvoiceLine.getQuantity().getUomId(), is(UomId.ofRepoId(uom1.getC_UOM_ID())));
		assertThat(customsInvoiceLine.getLineNetAmt(), is(expectedLineNetAmt));
	}

	private I_C_OrderLine createOrderLine(
			final OrderId order,
			final StockQtyAndUOMQty stockQtyAndUOMQty,
			final Money priceActual)
	{
		final I_C_OrderLine orderLineRecord = newInstance(I_C_OrderLine.class);

		orderLineRecord.setC_Order_ID(order.getRepoId());

		orderLineRecord.setM_Product_ID(stockQtyAndUOMQty.getProductId().getRepoId());
		orderLineRecord.setQtyOrdered(stockQtyAndUOMQty.getStockQty().toBigDecimal());

		orderLineRecord.setQtyEntered(stockQtyAndUOMQty.getUOMQtyNotNull().toBigDecimal());
		orderLineRecord.setC_UOM_ID(stockQtyAndUOMQty.getUOMQtyNotNull().getUomId().getRepoId());

		orderLineRecord.setPriceActual(priceActual.toBigDecimal());
		orderLineRecord.setC_Currency_ID(priceActual.getCurrencyId().getRepoId());

		save(orderLineRecord);

		return orderLineRecord;
	}

	private I_M_InOutLine createInOutLine(@NonNull final InOutId inout1, @NonNull final I_C_OrderLine orderLineRecord)
	{

		final I_M_InOutLine shipmentLineRecord = newInstance(I_M_InOutLine.class);
		shipmentLineRecord.setM_InOut_ID(inout1.getRepoId());

		shipmentLineRecord.setM_Product_ID(orderLineRecord.getM_Product_ID());
		shipmentLineRecord.setMovementQty(orderLineRecord.getQtyOrdered());

		shipmentLineRecord.setC_UOM_ID(orderLineRecord.getC_UOM_ID());
		shipmentLineRecord.setQtyEntered(orderLineRecord.getQtyEntered());

		shipmentLineRecord.setC_OrderLine_ID(orderLineRecord.getC_OrderLine_ID());

		save(shipmentLineRecord);

		return shipmentLineRecord;
	}

	private InOutId createShipment(final BPartnerLocationId bpartnerAndLocation)
	{
		final I_M_InOut shipment = newInstance(I_M_InOut.class);

		shipment.setC_BPartner_ID(bpartnerAndLocation.getBpartnerId().getRepoId());
		shipment.setC_BPartner_Location_ID(bpartnerAndLocation.getRepoId());

		shipment.setDocStatus(IDocument.STATUS_Completed);

		save(shipment);

		return InOutId.ofRepoId(shipment.getM_InOut_ID());
	}

	private OrderId createOrder(final BPartnerLocationId bpartnerAndLocation)
	{
		final I_M_Warehouse warehouse = newInstance(I_M_Warehouse.class);
		save(warehouse);

		final I_C_PaymentTerm paymentTerm = newInstance(I_C_PaymentTerm.class);
		save(paymentTerm);

		final I_C_Order order = newInstance(I_C_Order.class);

		order.setC_BPartner_ID(bpartnerAndLocation.getBpartnerId().getRepoId());
		order.setC_BPartner_Location_ID(bpartnerAndLocation.getRepoId());

		order.setDocStatus(DocStatus.Completed.getCode());

		order.setM_Warehouse_ID(warehouse.getM_Warehouse_ID());

		order.setC_PaymentTerm_ID(paymentTerm.getC_PaymentTerm_ID());

		order.setDatePromised(TimeUtil.asTimestamp(LocalDate.of(2019, 6, 6)));

		save(order);

		return OrderId.ofRepoId(order.getC_Order_ID());
	}

	private ProductId createProduct(final String productName, final I_C_UOM uom1)
	{
		final I_M_Product product = newInstance(I_M_Product.class);
		product.setName(productName);
		product.setC_UOM_ID(uom1.getC_UOM_ID());

		save(product);

		return ProductId.ofRepoId(product.getM_Product_ID());
	}

	private I_C_UOM createUOM(final String name)
	{
		final I_C_UOM uom = newInstance(I_C_UOM.class);
		uom.setName(name);
		uom.setUOMSymbol(name);
		uom.setStdPrecision(2);
		uom.setX12DE355(name);
		save(uom);

		return uom;
	}

	private UserId createUser(final BPartnerId bpartnerId, final String userName)
	{
		final I_AD_User userRecord = newInstance(I_AD_User.class);
		userRecord.setC_BPartner_ID(bpartnerId.getRepoId());
		userRecord.setName(userName);
		save(userRecord);

		return UserId.ofRepoId(userRecord.getAD_User_ID());
	}

	private DocTypeId createDocType(final String docTypeName)
	{
		final I_C_DocType docTypeRecord = newInstance(I_C_DocType.class);
		docTypeRecord.setName(docTypeName);
		save(docTypeRecord);

		return DocTypeId.ofRepoId(docTypeRecord.getC_DocType_ID());
	}

	private BPartnerLocationId createBPartnerAndLocation(final String partnerName, final String address)
	{
		final I_C_BPartner bpartnerRecord = newInstance(I_C_BPartner.class);
		bpartnerRecord.setName(partnerName);
		save(bpartnerRecord);

		final int bpartnerId = bpartnerRecord.getC_BPartner_ID();

		final I_C_BPartner_Location bpLocationRecord = newInstance(I_C_BPartner_Location.class);
		bpLocationRecord.setC_BPartner_ID(bpartnerId);
		bpLocationRecord.setAddress(address);

		save(bpLocationRecord);

		return BPartnerLocationId.ofRepoId(bpartnerId, bpLocationRecord.getC_BPartner_Location_ID());

	}

	private void createUOMConversion(
			final ProductId productId,
			final I_C_UOM uomFrom,
			final I_C_UOM uomTo,
			final BigDecimal multiplyRate,
			final BigDecimal divideRate)
	{
		createUOMConversion(CreateUOMConversionRequest.builder()
				.productId(productId)
				.fromUomId(UomId.ofRepoId(uomFrom.getC_UOM_ID()))
				.toUomId(UomId.ofRepoId(uomTo.getC_UOM_ID()))
				.fromToMultiplier(multiplyRate)
				.toFromMultiplier(divideRate)
				.build());
	}

	private void createUOMConversion(@NonNull final CreateUOMConversionRequest request)
	{
		Services.get(IUOMConversionDAO.class).createUOMConversion(request);
	}

}
