package de.metas.attachments;

import static org.adempiere.model.InterfaceWrapperHelper.newInstance;
import static org.adempiere.model.InterfaceWrapperHelper.saveRecord;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.adempiere.test.AdempiereTestHelper;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.compiere.model.I_C_BPartner;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import de.metas.adempiere.model.I_M_Product;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2018 metas GmbH
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

public class AttachmentEntryServiceTest
{
	private AttachmentEntryService attachmentEntryService;
	private I_C_BPartner bpartnerRecord;
	private I_M_Product productRecord;
	private AttachmentEntry bpartnerAttachmentEntry1;
	private AttachmentEntry bpartnerAttachmentEntry2;

	@Before
	public void init()
	{
		AdempiereTestHelper.get().init();

		bpartnerRecord = newInstance(I_C_BPartner.class);
		saveRecord(bpartnerRecord);

		productRecord = newInstance(I_M_Product.class);
		saveRecord(productRecord);

		attachmentEntryService = AttachmentEntryService.createInstanceForUnitTesting();

		bpartnerAttachmentEntry1 = attachmentEntryService.createNewAttachment(bpartnerRecord, "bPartnerAttachment1", "bPartnerAttachment1.data".getBytes());
		bpartnerAttachmentEntry2 = attachmentEntryService.createNewAttachment(bpartnerRecord, "bPartnerAttachment2", "bPartnerAttachment2.data".getBytes());
	}

	@Test
	public void linkEntriesToModel()
	{
		// invoke the method under test
		attachmentEntryService.linkAttachmentsToModels(ImmutableList.of(bpartnerAttachmentEntry1), TableRecordReference.ofCollection(ImmutableList.of(productRecord)));

		// assert that bpartnerRecord's attachments are unchanged
		final List<AttachmentEntry> bpartnerRecordEntries = attachmentEntryService.getByReferencedRecord(bpartnerRecord);
		assertThat(bpartnerRecordEntries).hasSize(2);
		assertThat(bpartnerRecordEntries.get(0)).isEqualTo(bpartnerAttachmentEntry1);
		assertThat(bpartnerRecordEntries.get(1)).isEqualTo(bpartnerAttachmentEntry2);

		final List<AttachmentEntry> productRecordEntries = attachmentEntryService.getByReferencedRecord(productRecord);
		assertThat(productRecordEntries).hasSize(1);
		// we need to compare them without linked records because productRecordEntries.get(0) has the product and bpartnerAttachmentEntry1 has the bpartner
		assertThat(productRecordEntries.get(0).withoutLinkedRecords()).isEqualTo(bpartnerAttachmentEntry1.withoutLinkedRecords());
	}

	@Test
	public void getEntries()
	{
		attachmentEntryService.linkAttachmentsToModels(ImmutableList.of(bpartnerAttachmentEntry1), TableRecordReference.ofCollection(ImmutableList.of(productRecord)));

		attachmentEntryService.createNewAttachment(bpartnerRecord, "bPartnerAttachment3", "bPartnerAttachment3.data".getBytes());

		// invoke the method under test
		final List<AttachmentEntry> productRecordEntries = attachmentEntryService.getByReferencedRecord(productRecord);

		// the entries to productRecord shall not be changed by the addition uf an entry for bpartnerRecord
		assertThat(productRecordEntries).hasSize(1);
		// we need to compare them without linked records because productRecordEntries.get(0) has the product and bpartnerAttachmentEntry1 has the bpartner
		assertThat(productRecordEntries.get(0).withoutLinkedRecords()).isEqualTo(bpartnerAttachmentEntry1.withoutLinkedRecords());
	}

	@Test
	public void deleteEntryForModel()
	{
		I_M_Product productRecord2 = newInstance(I_M_Product.class);
		saveRecord(productRecord2);

		final AttachmentEntry entry = attachmentEntryService.createNewAttachment(productRecord2, "productRecord2", "productRecord2.data".getBytes());

		attachmentEntryService.linkAttachmentsToModels(ImmutableList.of(entry), TableRecordReference.ofCollection(ImmutableList.of(productRecord2)));

		// invoke the method under test
		attachmentEntryService.unattach(TableRecordReference.of(productRecord), entry);

		assertThat(attachmentEntryService.getByReferencedRecord(productRecord)).isEmpty();

		final List<AttachmentEntry> entriesOfProductRecord2 = attachmentEntryService.getByReferencedRecord(productRecord2);
		assertThat(entriesOfProductRecord2).hasSize(1);
		assertThat(entriesOfProductRecord2.get(0)).isEqualTo(entry);
	}

}