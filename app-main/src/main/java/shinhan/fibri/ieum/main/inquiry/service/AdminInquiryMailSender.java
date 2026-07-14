package shinhan.fibri.ieum.main.inquiry.service;

import java.util.concurrent.CompletableFuture;

public interface AdminInquiryMailSender {

	CompletableFuture<Void> sendToAdmin(String requesterEmail, String title, String content);
}
