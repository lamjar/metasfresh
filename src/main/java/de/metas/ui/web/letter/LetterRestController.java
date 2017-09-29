package de.metas.ui.web.letter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Services;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.apache.commons.io.FileUtils;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

import de.metas.i18n.IMsgBL;
import de.metas.letters.api.ITextTemplateBL;
import de.metas.letters.model.I_C_Letter;
import de.metas.letters.model.Letters;
import de.metas.letters.model.MADBoilerPlate;
import de.metas.letters.model.MADBoilerPlate.BoilerPlateContext;
import de.metas.printing.esb.base.util.Check;
import de.metas.ui.web.config.WebConfig;
import de.metas.ui.web.letter.WebuiLetter.WebuiLetterBuilder;
import de.metas.ui.web.letter.json.JSONLetter;
import de.metas.ui.web.letter.json.JSONLetterRequest;
import de.metas.ui.web.session.UserSession;
import de.metas.ui.web.window.datatypes.DocumentPath;
import de.metas.ui.web.window.datatypes.LookupValue;
import de.metas.ui.web.window.datatypes.json.JSONDocumentChangedEvent;
import de.metas.ui.web.window.datatypes.json.JSONDocumentPath;
import de.metas.ui.web.window.datatypes.json.JSONLookupValue;
import de.metas.ui.web.window.datatypes.json.JSONLookupValuesList;
import de.metas.ui.web.window.model.DocumentCollection;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiOperation;
import lombok.Builder;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2017 metas GmbH
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

@RestController
@RequestMapping(LetterRestController.ENDPOINT)
@ApiModel("Letter endpoint")
public class LetterRestController
{
	public static final String ENDPOINT = WebConfig.ENDPOINT_ROOT + "/letter";

	@Autowired
	private UserSession userSession;

	@Autowired
	private WebuiLetterRepository lettersRepo;

	@Autowired
	private DocumentCollection documentCollection;

	private static final String PATCH_FIELD_Message = "message";
	private static final String PATCH_FIELD_TemplateId = "templateId";
	private static final Set<String> PATCH_FIELD_ALL = ImmutableSet.of(PATCH_FIELD_Message, PATCH_FIELD_TemplateId);

	private final void assertReadable(final WebuiLetter letter)
	{
		// Make sure current logged in user is the owner
		final int loggedUserId = userSession.getAD_User_ID();
		if (letter.getOwnerUserId() != loggedUserId)
		{
			throw new AdempiereException("No credentials to read the letter")
					.setParameter("letterId", letter.getLetterId())
					.setParameter("ownerUserId", letter.getOwnerUserId())
					.setParameter("loggedUserId", loggedUserId);
		}
	}

	private final void assertWritable(final WebuiLetter letter)
	{
		assertReadable(letter);

		// Make sure the letter was not already processed
		if (letter.isProcessed())
		{
			throw new AdempiereException("Cannot change an letter which was already processed")
					.setParameter("letterId", letter.getLetterId());
		}
	}

	@PostMapping
	@ApiOperation("Creates a new letter")
	public JSONLetter createNewLetter(@RequestBody final JSONLetterRequest request)
	{
		userSession.assertLoggedIn();

		final DocumentPath contextDocumentPath = JSONDocumentPath.toDocumentPathOrNull(request.getDocumentPath());
		final I_C_Letter persistentLetter = fromLetterBuilder()
				.content("")
				.subject("")
				.build();
		final WebuiLetter letter = lettersRepo.createNewLetter(userSession.getAD_User_ID(), contextDocumentPath, persistentLetter.getC_Letter_ID());

		return JSONLetter.of(letter);
	}

	@GetMapping("/{letterId}")
	@ApiOperation("Gets letter by ID")
	public JSONLetter getLetter(@PathVariable("letterId") final String letterId)
	{
		userSession.assertLoggedIn();
		final WebuiLetter letter = lettersRepo.getLetter(letterId);
		assertReadable(letter);
		return JSONLetter.of(letter);
	}

	@Builder(builderMethodName = "fromLetterBuilder")
	private I_C_Letter createPersistentLetter(final String subject, final String content)
	{
		final I_C_Letter persistentLetter = InterfaceWrapperHelper.newInstance(I_C_Letter.class);
		persistentLetter.setLetterSubject(subject);
		persistentLetter.setLetterBody(content);
		persistentLetter.setLetterBodyParsed(content);
		InterfaceWrapperHelper.save(persistentLetter);
		return persistentLetter;
	}

	private I_C_Letter updatePersistentLetter(final WebuiLetter letter)
	{
		Check.assume(letter.getPersistentLetterId() > 0, "Letter ID should be > 0");
		final Properties ctx = Env.getCtx();
		final int C_BPartner_ID = Env.getContextAsInt(ctx, I_C_Letter.COLUMNNAME_C_BPartner_ID);
		final int C_BPartner_Location_ID = Env.getContextAsInt(ctx, I_C_Letter.COLUMNNAME_C_BPartner_Location_ID);
		final String bpartnerAddress = Env.getContext(ctx, I_C_Letter.COLUMNNAME_BPartnerAddress);
		
		final I_C_Letter persistentLetter = InterfaceWrapperHelper.create(ctx, letter.getPersistentLetterId(), I_C_Letter.class, ITrx.TRXNAME_ThreadInherited);
		persistentLetter.setLetterSubject(letter.getSubject());
		// field is mandatory
		persistentLetter.setLetterBody(Joiner.on(" ").skipNulls().join(Arrays.asList(letter.getContent())));
		persistentLetter.setLetterBodyParsed(letter.getContent());
		persistentLetter.setC_BPartner_ID(C_BPartner_ID);
		persistentLetter.setC_BPartner_Location_ID(C_BPartner_Location_ID);
		persistentLetter.setBPartnerAddress(bpartnerAddress);
		InterfaceWrapperHelper.save(persistentLetter);
		return persistentLetter;
	}

	private File createPDFFile(final WebuiLetter letter)
	{
		final I_C_Letter persistentLetter = updatePersistentLetter(letter);
		byte[] pdf = Services.get(ITextTemplateBL.class).createPDF(persistentLetter);
		final String pdfFilenamePrefix = Services.get(IMsgBL.class).getMsg(Env.getCtx(), Letters.MSG_Letter);
		File pdfFile = null;
		try
		{
			pdfFile = File.createTempFile(pdfFilenamePrefix, ".pdf");
			FileUtils.writeByteArrayToFile(pdfFile, pdf);
		}
		catch (IOException e)
		{
			AdempiereException.wrapIfNeeded(e);
		}

		return pdfFile;
	}

	private ResponseEntity<byte[]> createPDFResponseEntry(final File pdfFile)
	{
		final String pdfFilename = pdfFile.getName();
		final byte[] pdfData = Util.readBytes(pdfFile);

		final HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + pdfFilename + "\"");
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		final ResponseEntity<byte[]> response = new ResponseEntity<>(pdfData, headers, HttpStatus.OK);
		return response;
	}

	@GetMapping("/{letterId}/printPreview")
	@ApiOperation("Returns letter's printable version (e.g. PDF)")
	public ResponseEntity<byte[]> getLetterPrintPreview(@PathVariable("letterId") final String letterId)
	{
		userSession.assertLoggedIn();

		//
		// Get the letter
		final WebuiLetter letter = lettersRepo.getLetter(letterId);
		assertReadable(letter);

		//
		// Create and return the printable letter
		final File pdfFile = createPDFFile(letter);
		return createPDFResponseEntry(pdfFile);
	}

	@PostMapping("/{letterId}/complete")
	@ApiOperation("Completes the letter and returns it's printable version (e.g. PDF)")
	public ResponseEntity<byte[]> complete(@PathVariable("letterId") final String letterId)
	{
		userSession.assertLoggedIn();

		final WebuiLetterChangeResult result = changeLetter(letterId, letter -> {

			//
			// Create the printable letter
			final File pdfFile = createPDFFile(letter);

			//
			// create the Boilerplate context
			final BoilerPlateContext context = documentCollection.createBoilerPlateContext(letter.getContextDocumentPath());
			//
			// Create the request
			final TableRecordReference recordRef = documentCollection.getTableRecordReference(letter.getContextDocumentPath());
			MADBoilerPlate.createRequest(pdfFile, recordRef.getAD_Table_ID(), recordRef.getRecord_ID(), context);

			return letter.toBuilder()
					.processed(true)
					.temporaryPDFFile(pdfFile)
					.build();
		});

		//
		// Remove the letter
		lettersRepo.removeLetterById(letterId);

		//
		// Return the printable letter
		return createPDFResponseEntry(result.getLetter().getTemporaryPDFFile());
	}

	@PatchMapping("/{letterId}")
	@ApiOperation("Changes the letter")
	public JSONLetter changeLetter(@PathVariable("letterId") final String letterId, @RequestBody final List<JSONDocumentChangedEvent> events)
	{
		userSession.assertLoggedIn();

		final WebuiLetterChangeResult result = changeLetter(letterId, letterOld -> changeLetter(letterOld, events));
		return JSONLetter.of(result.getLetter());
	}

	private WebuiLetterChangeResult changeLetter(final String letterId, final UnaryOperator<WebuiLetter> letterModifier)
	{
		final WebuiLetterChangeResult result = lettersRepo.changeLetter(letterId, letterOld -> {
			assertWritable(letterOld);
			return letterModifier.apply(letterOld);
		});

		return result;
	}

	private WebuiLetter changeLetter(final WebuiLetter letter, final List<JSONDocumentChangedEvent> events)
	{
		final WebuiLetterBuilder letterBuilder = letter.toBuilder();
		events.forEach(event -> changeLetter(letter, letterBuilder, event));
		return letterBuilder.build();
	}

	private void changeLetter(final WebuiLetter letter, final WebuiLetter.WebuiLetterBuilder newLetterBuilder, final JSONDocumentChangedEvent event)
	{
		if (!event.isReplace())
		{
			throw new AdempiereException("Unsupported event")
					.setParameter("event", event);
		}

		final String fieldName = event.getPath();
		if (PATCH_FIELD_Message.equals(fieldName))
		{
			final String message = event.getValueAsString(null);
			newLetterBuilder.content(message);
		}
		else if (PATCH_FIELD_TemplateId.equals(fieldName))
		{
			@SuppressWarnings("unchecked")
			final LookupValue templateId = JSONLookupValue.integerLookupValueFromJsonMap((Map<String, String>)event.getValue());
			applyTemplate(letter, newLetterBuilder, templateId);
		}
		else
		{
			throw new AdempiereException("Unsupported event path")
					.setParameter("event", event)
					.setParameter("fieldName", fieldName)
					.setParameter("availablePaths", PATCH_FIELD_ALL);
		}
	}

	@GetMapping("/templates")
	@ApiOperation("Available Email templates")
	public JSONLookupValuesList getTemplates()
	{
		return MADBoilerPlate.getAll(userSession.getCtx())
				.stream()
				.map(adBoilerPlate -> JSONLookupValue.of(adBoilerPlate.getAD_BoilerPlate_ID(), adBoilerPlate.getName()))
				.collect(JSONLookupValuesList.collect());
	}

	private void applyTemplate(final WebuiLetter letter, final WebuiLetterBuilder newLetterBuilder, final LookupValue templateId)
	{
		final Properties ctx = userSession.getCtx();
		final MADBoilerPlate boilerPlate = MADBoilerPlate.get(ctx, templateId.getIdAsInt());

		//
		// Attributes
		final BoilerPlateContext context = documentCollection.createBoilerPlateContext(letter.getContextDocumentPath());

		//
		// Content and subject
		newLetterBuilder.content(boilerPlate.getTextSnippetParsed(context));
		newLetterBuilder.subject(boilerPlate.getSubject());
	}

}
