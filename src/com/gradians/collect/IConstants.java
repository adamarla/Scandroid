package com.gradians.collect;

public interface IConstants {
    
    String WEB_APP_HOST_PORT = "www.gradians.com";
    String BANK_HOST_PORT = "109.74.201.62:8080";
    String TAG = "com.gradians.collect";
    String TAG_ID = "com.gradians.collect.ID";
    String TOKEN_KEY = "token";
    String NAME_KEY = "name";
    String EMAIL_KEY = "email";
    String ITEMS_KEY = "gradeables";
    String QUIZ_ID_KEY = "quizId";
    String QUIZ_NAME_KEY = "quiz";
    String QUIZ_PATH_KEY = "locn";
    String STATE_KEY = "state";
    String GR_ID_KEY = "id";
    String GR_PATH_KEY = "img";
    String QUESTIONS_DIR_NAME = "questions";
    String ANSWERS_DIR_NAME = "answers";
    String SOLUTIONS_DIR_NAME = "solutions";
    String MANIFEST_DIR_NAME = "manifests";
    char WAITING = 'W',
         DOWNLOADED = 'D',
         CAPTURED = 'C',
         UNMARKED = 'U',
         MARKED = 'M', 
         SAVED = 'V', 
         SENT = 'S';
    
}
