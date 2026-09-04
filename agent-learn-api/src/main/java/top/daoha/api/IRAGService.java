package top.daoha.api;


import org.springframework.web.multipart.MultipartFile;
import top.daoha.api.response.Response;

import java.util.List;

public interface IRAGService {

    Response<List<String>> queryRagTagList();

    Response<String> uploadFile(String ragTag,List<MultipartFile> files);

    Response<String> analyzeGitRepository(String reUrl,String userName,String token) throws Exception;
}
