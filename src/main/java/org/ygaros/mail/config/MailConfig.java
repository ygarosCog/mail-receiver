package org.ygaros.mail.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.mail.ImapMailReceiver;
import org.springframework.integration.mail.MailReceiver;
import org.springframework.integration.mail.MailReceivingMessageSource;
import org.springframework.messaging.Message;
import org.ygaros.mail.service.MailReceiverService;

import javax.mail.internet.MimeMessage;

@Configuration
@EnableIntegration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);
    @Autowired
    private MailReceiverService receiverService;

    @Value("${spring.mail.username}")
    private String USERNAME;

    @Value("${spring.mail.password}")
    private String PASSWORD;

    @Value("${spring.mail.properties.port}")
    private int IMAP_PORT;

    @Value("${spring.mail.properties.protocol}")
    private String IMAP_PROTOCOL;

    @Value("${spring.mail.properties.host}")
    private String IMAP_HOST;

    /*
        Define a channel where we send and get the messages (mails in this example).
        MessageSource is sending messages to this channel.
        DirectChannel is a SubscribableChannel.
     */
    @Bean("incomingEmailsChannel")
    public DirectChannel defaultChannel() {
        DirectChannel directChannel = new DirectChannel();
        directChannel.setDatatypes(MimeMessage.class);
        return directChannel;
    }
    @Bean
    public MailReceiver imapMailReceiver() {
        String url = String.format(
                "%s://%s:%s@%s:%s/INBOX", IMAP_PROTOCOL, USERNAME, PASSWORD, IMAP_HOST, IMAP_PORT
        );
        log.debug("IMAP url: {}", url.replace(PASSWORD, "x".repeat(8)));

        ImapMailReceiver imapMailReceiver = new ImapMailReceiver(url);
        imapMailReceiver.setShouldMarkMessagesAsRead(true);
        imapMailReceiver.setShouldDeleteMessages(false);
        /*
            Attach content to message because by default the MimeMessage
            doesn't contain content body.
         */
        imapMailReceiver.setSimpleContent(true);
        //imapMailReceiver.setMaxFetchSize(1);

        return imapMailReceiver;
    }

    /*
      Provide MessageSource of Mails as spring integration Messages from ImapMailReceiver to be sent
      through incomingEmailsChannel.
      Poller with defined rate at which the messages are pushed to the channel
      (if there are any) every 5 sec.
   */
    @Bean
    @InboundChannelAdapter(
            channel = "incomingEmailsChannel",
            poller = @Poller(fixedDelay = "5000")

    )
    public MailReceivingMessageSource mailMessageSource(MailReceiver mailReceiver) {
        MailReceivingMessageSource mailReceivingMessageSource = new MailReceivingMessageSource(mailReceiver);
        return mailReceivingMessageSource;

    }

    /*
        Connect (subscribe) to incomingEmailsChannel channel and provide method to
        handle each individual messages.
     */
    @ServiceActivator(inputChannel = "incomingEmailsChannel")
    public void receive(Message<MimeMessage> message) {
        receiverService.handleReceivedMail(message.getPayload());
    }
}