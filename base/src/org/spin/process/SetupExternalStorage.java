/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2015 E.R.P. Consultores y Asociados, C.A.               *
 * All Rights Reserved.                                                       *
 * Contributor(s): Yamel Senih www.erpya.com                                  *
 *****************************************************************************/

package org.spin.process;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.compiere.model.I_AD_Attachment;
import org.compiere.model.MArchive;
import org.compiere.model.MAttachment;
import org.compiere.model.MImage;
import org.compiere.model.Query;
import org.compiere.util.DB;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.compiere.util.TrxRunnable;
import org.spin.util.AttachmentUtil;

/** Generated Process for (Setup External Storage for Files)
 * @author Yamel Senih, ySenih@erpya.com, ERPCyA http://www.erpya.com
 *		Add Support to external storage for attachment
 */
public class SetupExternalStorage extends SetupExternalStorageAbstract {

	/**	Processed Files	*/
	private AtomicInteger processed = new AtomicInteger();
	/**	Ignored Files	*/
	private AtomicInteger ignored = new AtomicInteger();
	/**	Errors	*/
	private AtomicInteger errors = new AtomicInteger();
	
	@Override
	protected String doIt() throws Exception {
		//	Add Attachment
		migrateAttachment("Attachment");
		//	Add Image
		migrateImage("Image");
		//	Add Archive
		migrateArchive("Archive");
		return "@Processed@ (" + processed.get() + ") @Ignored@ (" + ignored.get() + ") @Errors@ (" + errors.get() + ")";
	}
	
	/**
	 * Add attachment
	 * @param attachmentFolder
	 */
	private void migrateAttachment(String attachmentFolder) {
		addLog("@AD_Attachment_ID@");
		new Query(getCtx(), I_AD_Attachment.Table_Name, null, get_TrxName())
				.setClient_ID()
				.<MAttachment>list()
				.forEach(attachment -> {
					Arrays.asList(attachment.getEntries())
						.forEach(entry -> {
							Trx.run(new TrxRunnable() {
								public void run(String trxName) {
									//	Find existing reference
									int attachmentReferenceId = DB.getSQLValue(get_TrxName(), "SELECT AD_AttachmentReference_ID "
											+ "FROM AD_AttachmentReference "
											+ "WHERE AD_Attachment_ID = ? "
											+ "AND FileName = ?", attachment.getAD_Attachment_ID(), entry.getName());
									if(attachmentReferenceId < 0) {
										try {
											AttachmentUtil.getInstance(getCtx())
												.withAppRegistrationId(getAppRegistrationId())
												.withAttachmentId(attachment.getAD_Attachment_ID())
												.withPath(attachmentFolder)
												.withFileName(entry.getName())
												.withDescription(Msg.getMsg(getCtx(), "CreatedFromSetupExternalStorage"))
												.withData(entry.getData())
												.saveAttachment();
											addLog(entry.getName() + ": @Ok@");
											processed.incrementAndGet();
										} catch (Exception e) {
											log.warning("Error: " + e.getLocalizedMessage());
											addLog("@ErrorProcessingFile@ " + entry.getName() + ": " + e.getLocalizedMessage());
											errors.incrementAndGet();
										}
									} else {
										addLog(entry.getName() + ": @Ignored@");
										ignored.incrementAndGet();
									}
								}
							});
						});
				});
	}
	
	/**
	 * Add images
	 * @param attachmentFolder
	 */
	private void migrateImage(String attachmentFolder) {
		addLog("@AD_Image_ID@");
		KeyNamePair [] imageArray = DB.getKeyNamePairs("SELECT AD_Image_ID, Name "
				+ "FROM AD_Image "
				+ "WHERE AD_Client_ID = ? "
				+ "AND NOT EXISTS(SELECT 1 FROM AD_AttachmentReference ar WHERE ar.AD_Image_ID = AD_Image.AD_Image_ID)", false, getAD_Client_ID());
		Arrays.asList(imageArray)
			.forEach(imagePair -> {
				Trx.run(new TrxRunnable() {
					public void run(String trxName) {
						MImage image = MImage.get(getCtx(), imagePair.getKey());
						try {
							AttachmentUtil.getInstance(getCtx())
								.withAppRegistrationId(getAppRegistrationId())
								.withImageId(image.getAD_Image_ID())
								.withPath(attachmentFolder)
								.withFileName(image.getName())
								.withDescription(Msg.getMsg(getCtx(), "CreatedFromSetupExternalStorage"))
								.withData(image.getData())
								.saveAttachment();
							addLog(image.getName() + ": @Ok@");
							processed.incrementAndGet();
						} catch (Exception e) {
							log.warning("Error: " + e.getLocalizedMessage());
							addLog("@ErrorProcessingFile@ " + image.getName() + ": " + e.getLocalizedMessage());
							errors.incrementAndGet();
						}
					}
				});
			});
	}
	
	/**
	 * Add archive
	 * @param attachmentFolder
	 */
	private void migrateArchive(String attachmentFolder) {
		addLog("@AD_Archive_ID@");
		KeyNamePair [] archiveArray = DB.getKeyNamePairs("SELECT AD_Archive_ID, Name "
				+ "FROM AD_Archive "
				+ "WHERE AD_Client_ID = ? "
				+ "AND NOT EXISTS(SELECT 1 FROM AD_AttachmentReference ar WHERE ar.AD_Archive_ID = AD_Archive.AD_Archive_ID)", false, getAD_Client_ID());
		Arrays.asList(archiveArray)
			.forEach(archivePair -> {
				Trx.run(new TrxRunnable() {
					public void run(String trxName) {
						MArchive archive = new MArchive(getCtx(), archivePair.getKey(), trxName);
						try {
							String fileName = archive.getName() + ".pdf";
							AttachmentUtil.getInstance(getCtx())
								.withAppRegistrationId(getAppRegistrationId())
								.withArchiveId(archive.getAD_Archive_ID())
								.withPath(attachmentFolder)
								.withFileName(fileName)
								.withDescription(Msg.getMsg(getCtx(), "CreatedFromSetupExternalStorage"))
								.withData(archive.getBinaryData())
								.saveAttachment();
							addLog(fileName + ": @Ok@");
							processed.incrementAndGet();
						} catch (Exception e) {
							log.warning("Error: " + e.getLocalizedMessage());
							addLog("@ErrorProcessingFile@ " + archive.getName() + ": " + e.getLocalizedMessage());
							errors.incrementAndGet();
						}
					}
				});
			});
	}
}