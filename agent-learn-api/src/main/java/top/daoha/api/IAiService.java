package top.daoha.api;



import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;


public interface IAiService {

    ChatResponse generate(String message);

    Flux<String> generateStream(String chatId, String message);

    Flux<String> generateStreamRag(String chatId, String ragTag, String message);
}
