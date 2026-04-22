package com.project.quiz_service.service;

import com.project.quiz_service.dao.QuizDao;
import com.project.quiz_service.feign.QuizInterface;
import com.project.quiz_service.model.QuestionWrapper;
import com.project.quiz_service.model.Quiz;
import com.project.quiz_service.model.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class QuizService {

    @Autowired
    private QuizDao quizDao;

    @Autowired
    QuizInterface quizInterface;


    public ResponseEntity<String> createQuiz(String category, int numQ, String title) {

        List<Integer> questions = quizInterface.getQuestionsForQuiz(category,numQ).getBody();
        Quiz quiz = new Quiz();
        quiz.setTitle(title);
        quiz.setQuestionsIds(questions);
        quizDao.save(quiz);

        return new ResponseEntity<>("Success", HttpStatus.CREATED);
    }

    public ResponseEntity<List<QuestionWrapper>> getQuizQuestions(Integer id) {
        Quiz quiz = quizDao.findById(id).get();
        List<Integer> questionIds = quiz.getQuestionsIds();

        ResponseEntity<List<QuestionWrapper>> questionsResponse =  quizInterface.getQuestionsFromId(questionIds);

        List<QuestionWrapper> questions = questionsResponse.getBody();
        return new ResponseEntity<>(questions, HttpStatus.OK);    }

    public ResponseEntity<Integer> calculateResult(Integer id, List<Response> responses) {

        ResponseEntity<Integer> score = quizInterface.getScore(responses);
        return score;
    }
}
