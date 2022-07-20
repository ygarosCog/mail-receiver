package org.ygaros.mail.service;

import org.apache.commons.mail.util.MimeMessageParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import javax.activation.DataSource;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class MailReceiverService {

    private static final Logger log = LoggerFactory.getLogger(MailReceiverService.class);
    private static final String ATTACHMENTS_FOLDER_STORE = "data";

    public void handleReceivedMail(MimeMessage receivedMessage){
        try {
            MimeMessageParser mimeMessageParser = new MimeMessageParser(receivedMessage);
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
                log.error("An error: {} on creating: {}", e, directoryPath);
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

