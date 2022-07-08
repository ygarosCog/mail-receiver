package org.ygaros.mail.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.apache.commons.mail.util.MimeMessageParser;
import org.springframework.stereotype.Service;

import javax.activation.DataSource;
import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class MailReceiverService {

    private static final Logger log = LoggerFactory.getLogger(MailReceiverService.class);
    private static final String ATTACHMENTS_FOLDER_STORE = "data";

    public void handleReceivedMail(MimeMessage receivedMessage) {
        try (Folder folder = receivedMessage.getFolder()) {

            folder.open(Folder.READ_ONLY);
            //folder.open(Folder.READ_WRITE);

            Message[] messages = folder.getMessages();

            /*
                Define what info we want to fetch.
                It's not mandatory, we can reduce number of things to fetch.

            https://jakarta.ee/specifications/mail/1.6/apidocs/javax/mail/FetchProfile.Item.html
            */
            FetchProfile contentsProfile = new FetchProfile();
            contentsProfile.add(FetchProfile.Item.ENVELOPE);
            contentsProfile.add(FetchProfile.Item.CONTENT_INFO);
            contentsProfile.add(FetchProfile.Item.FLAGS);
            contentsProfile.add(FetchProfile.Item.SIZE);

            /*
                Prefetch attributes, based on the given FetchProfile.
                https://jakarta.ee/specifications/mail/1.6/apidocs/com/sun/mail/imap/imapfolder#fetch-javax.mail.Message:A-javax.mail.FetchProfile-
             */
            folder.fetch(messages, contentsProfile);


            /*
                We can fetch all mails inside INBOX (see imap url) folder which is default folder where new
                messages get saved, so we need to filter them by id that we only fetch the one we want to process.
            */
            for (Message value : messages) {
                MimeMessage mimeMessage = (MimeMessage) value;
                if (mimeMessage.getMessageID().equalsIgnoreCase(receivedMessage.getMessageID())) {
                    extractMail(mimeMessage);
                    break;
                }
            }

            /*
            receivedMessage.setFlag(Flags.Flag.DELETED, true); <- set flag value to delete message
                To not delete the marked with DELETED flag messages we can use.
            folder.close(false);
                The try-with-resources uses folder.close(true); which deletes these messages.
            */
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private void extractMail(MimeMessage message) {
        try {
            MimeMessageParser mimeMessageParser = new MimeMessageParser(message);
            mimeMessageParser.parse();

            logMail(mimeMessageParser);
            saveAttachments(mimeMessageParser);

            /*
                To delete downloaded email
                Setting those flag will delete messages only when we use folder.close(true);
            message.setFlag(Flags.Flag.DELETED, true);
             */
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
    private void logMail(MimeMessageParser mimeMessageParser) throws Exception {
        log.debug("From: {} To: {} Subject: {}",
                mimeMessageParser.getFrom(), mimeMessageParser.getTo(), mimeMessageParser.getSubject());
        log.debug("Mail content: {}", mimeMessageParser.getPlainContent());

    }

    private void saveAttachments(MimeMessageParser parser){
        List<DataSource> attachments = parser.getAttachmentList();
        log.debug("Email has {} attachment files", attachments.size());
        attachments.forEach(dataSource -> {
            String fileName = dataSource.getName();
            if(fileName != null && fileName.length() > 0){
                String rootDirPath = new FileSystemResource("").getFile().getAbsolutePath();

                createDirectoryIfNotExists(rootDirPath);

                String attachmentFilePath = rootDirPath +
                        File.separator +
                        ATTACHMENTS_FOLDER_STORE +
                        File.separator +
                        fileName;
                File attachmentFile = new File(attachmentFilePath);

                log.info("Attachment file saved to: {}", attachmentFilePath);

                try(
                        InputStream in = dataSource.getInputStream();
                        OutputStream out = new FileOutputStream(attachmentFile)
                        ){
                    copyStream(in, out);
                }catch(IOException e){
                    log.error("Failed to save file.", e);
                }
            }
        });
    }

    private void createDirectoryIfNotExists(String directoryPath) {
        Path of = Path.of(directoryPath);
        if (!Files.exists(of)) {
            try {
                Files.createDirectories(of);
            } catch (IOException e) {
                log.error("An error: {} occurred during create folder: {}", e, directoryPath);
            }
        }
    }

    private void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192]; //just my standard base buffer
        int readByteCount;
        while(-1 != (readByteCount = in.read(buffer))){
            out.write(buffer, 0, readByteCount);
        }
    }
}

