package top.daoha.test.ai;

import com.alibaba.fastjson.JSON;
import com.zaxxer.hikari.HikariDataSource;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class OpenAiTest {

    @Autowired
    private javax.sql.DataSource dataSource;


    @Value("classpath:data/dog.png")
    private Resource imageResource;

    @Value("classpath:data/file.txt")
    private Resource textResource;

    @Value("classpath:data/article-prompt-words.txt")
    private Resource articlePromptWordsResource;

    @Autowired
    private OpenAiChatModel openAiChatModel;

    @Autowired
    private PgVectorStore pgVectorStore;

    private final TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();

    @Test
    public void test_call() {
        ChatResponse response = openAiChatModel.call(new Prompt(
                "1+1",
                OpenAiChatOptions.builder()
                        .model("gpt-4o")
                        .build()));
        log.info("测试结果(call):{}", JSON.toJSONString(response));
    }

    @Test
    public void test_call_images() {
        UserMessage userMessage = UserMessage.builder()
                .text("请描述这张图片的主要内容，并说明图中物品的可能用途。")
                .media(org.springframework.ai.content.Media.builder()
                        .mimeType(MimeType.valueOf(MimeTypeUtils.IMAGE_PNG_VALUE))
                        .data(imageResource)
                        .build())
                .build();

        ChatResponse response = openAiChatModel.call(new Prompt(
                userMessage,
                OpenAiChatOptions.builder()
                        .model("gpt-4o")
                        .build()));

        log.info("测试结果(images):{}", JSON.toJSONString(response));
    }

    @Test
    public void test_stream() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Flux<ChatResponse> stream = openAiChatModel.stream(new Prompt(
                "1+1",
                OpenAiChatOptions.builder()
                        .model("gpt-4o")
                        .build()));

        stream.subscribe(
                chatResponse -> {
                    AssistantMessage output = chatResponse.getResult().getOutput();
                    log.info("测试结果(stream): {}", JSON.toJSONString(output));
                },
                Throwable::printStackTrace,
                () -> {
                    countDownLatch.countDown();
                    log.info("测试结果(stream): done!");
                }
        );

        countDownLatch.await();
    }

    @Test
    public void upload() {
        System.out.println("开始打印配置");
        // 直接强转，不获取真实的 Connection，就不会报错
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        System.out.println("====== 内存中实际读取的URL是: [" + hikariDataSource.getJdbcUrl() + "] ======");
        System.out.println("====== 用户名是: [" + hikariDataSource.getUsername() + "] ======");
        System.out.println("结束打印配置");
        CountDownLatch countDownLatch = new CountDownLatch(1);
        // textResource、articlePromptWordsResource
        TikaDocumentReader reader = new TikaDocumentReader(textResource);

        List<Document> documents = reader.get();
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "article-prompt-words"));

        pgVectorStore.accept(documentSplitterList);

        log.info("上传完成");
    }

    @Test
    public void chat() {
        String message = "王大瓜今年几岁";

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        SearchRequest request = SearchRequest.builder()
                .query(message)
                .topK(5)
                .filterExpression("knowledge == 'article-prompt-words'")
                .build();

        List<Document> documents = pgVectorStore.similaritySearch(request);

        String documentsCollectors = null == documents ? "" : documents.stream().map(Document::getText).collect(Collectors.joining());

        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentsCollectors));

        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        ChatResponse chatResponse = openAiChatModel.call(new Prompt(
                messages,
                OpenAiChatOptions.builder()
                        .model("gpt-4o")
                        .build()));

        log.info("测试结果:{}", JSON.toJSONString(chatResponse));
    }

    @Test
    public void exposeTheTruth() {
        System.out.println("====== 开始测谎 ======");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // 查询当前连接的这个 PostgreSQL 实例里面，到底有哪些数据库？
        List<String> databases = jdbcTemplate.queryForList("SELECT datname FROM pg_database WHERE datistemplate = false", String.class);

        System.out.println("====== 你的 Java 连上的 5432 端口里，真实存在的库有: " + databases + " ======");
        System.out.println("====== 测谎结束 ======");
    }

    /**
     * @ClassName : AiSearchMCPTest
     * @Description :
     * @github:
     * @Author : 24209
     * @Date: 2026/8/27  20:58
     */

    @Slf4j
    @RunWith(SpringRunner.class)
    @SpringBootTest
    public static class AiSearchMCPTest {

        @Test
        public void test() {
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder()
                            .baseUrl("https://api2.aigcbest.top")
                            .apiKey("sk-qN3s0oXo3UkMbQpfdB6GbxpaPXSRtcVR0ib3ic7X2jIIupMq")
                            .completionsPath("v1/chat/completions")
                            .embeddingsPath("v1/embeddings")
                            .build())
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model("gpt-5.4-mini")
                            .toolCallbacks(new SyncMcpToolCallbackProvider(sseMcpClient1()).getToolCallbacks())
                            .build())
                    .build();

            ChatResponse call = chatModel.call(Prompt.builder()
                                .messages(new UserMessage("请你告诉我怎么从信工所到天安门走公关交通"))
                            .build());
            log.info("测试结果:{}", JSON.toJSONString(call));
        }

        public McpSyncClient sseMcoClient(){
            HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder("http://appbuilder.baidu.com/v2/ai_search/mcp/")
                    .sseEndpoint("sse?api_key=bce-v3/ALTAK-JyRDSbohzwZh96BEArsls/6072237399822c7a9b16bc53fa52506d2205a056")
                    .build();

            McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport)
                    .requestTimeout(Duration.ofMinutes(1))
                    .build();

            var initialize = mcpSyncClient.initialize();
            log.info("Tool SSE MCP Initialized {}", initialize);

            return  mcpSyncClient;
        }
        public McpSyncClient sseMcpClient1(){
            HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder("https://mcp.amap.com")
                    .sseEndpoint("/sse?key=fe472f8e8467d5c0d4284a2c6a6222da")
                    .build();

            McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport)
                    .requestTimeout(Duration.ofMinutes(1))
                    .build();

            var initialize = mcpSyncClient.initialize();
            log.info("Tool SSE MCP Initialized {}", initialize);

            return  mcpSyncClient;
        }
    }
}
