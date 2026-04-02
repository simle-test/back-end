package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class QuestionService {

    // 1. 定义数据存储列表
    private List<Question> questionList = new ArrayList<>();

    // 2. 定义 ID 生成器
    private AtomicInteger idGenerator = new AtomicInteger(100);

    // 3. 定义 ObjectMapper
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 初始化：启动时加载 text.json
     */
    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("text.json");
            InputStream inputStream = resource.getInputStream();

            List<Question> loadedQuestions = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<Question>>() {}
            );

            this.questionList = loadedQuestions;
            System.out.println("✅ 成功加载题目：" + questionList.size());
            if (!questionList.isEmpty()) {
                // 注意这里也要改，从 content 里取
                System.out.println(">>> 调试：JSON里第一道题的正确答案是： " + questionList.get(0).getQuestion_content().getCorrect_answer());
            }
            // 更新 ID 计数器
            if (!questionList.isEmpty()) {
                int maxId = questionList.stream()
                        .mapToInt(Question::getQuestion_id)
                        .max()
                        .orElse(0);
                idGenerator.set(maxId + 1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取所有题目
     */
    public List<Question> getAllQuestions() {
        return questionList;
    }

    /**
     * 添加题目
     */
    public void addQuestion(Question question) {
        question.setQuestion_id(idGenerator.getAndIncrement());
        questionList.add(question);
    }

    /**
     * 删除题目
     */
    public void deleteQuestionById(Integer id) {
        Iterator<Question> iterator = questionList.iterator();
        while (iterator.hasNext()) {
            Question q = iterator.next();
            if (q.getQuestion_id().equals(id)) {
                iterator.remove();
                return;
            }
        }
        throw new RuntimeException("未找到 ID 为 " + id + " 的题目");
    }

    /**
     * 修改题目
     */
    public void updateQuestion(Question updatedQuestion) {
        for (int i = 0; i < questionList.size(); i++) {
            Question current = questionList.get(i);
            if (current.getQuestion_id().equals(updatedQuestion.getQuestion_id())) {
                questionList.set(i, updatedQuestion);
                return;
            }
        }
        throw new RuntimeException("未找到 ID 为 " + updatedQuestion.getQuestion_id() + " 的题目");
    }

    /**
     * 校验答案 (核心修改在这里)
     */
    public boolean checkAnswer(Question userAnswer) {
        for (Question q : questionList) {
            // 1. 先找到这道题
            if (q.getQuestion_id().equals(userAnswer.getQuestion_id())) {

                // 2. 拿出题库里的正确答案
                // 【修改点】必须通过 getQuestion_content() 进入内部对象获取
                String correctAnswer = q.getQuestion_content().getCorrect_answer();

                // 3. 拿出用户传来的选择
                String userSelected = userAnswer.getSelected_answer();

                // 4. 进行对比
                System.out.println("正确答案: " + correctAnswer + ", 用户选择: " + userSelected);

                return correctAnswer != null && correctAnswer.equals(userSelected);
            }
        }
        return false;
    }

    /**
     * 获取错题（暂时返回空）
     */
    public List<Question> getWrongQuestions() {
        return new ArrayList<>();
    }
}