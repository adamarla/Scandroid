package com.gradians.collect;

public interface IConstants {
    
    String WEB_APP_HOST_PORT = "www.gradians.com";
    String BANK_HOST_PORT = "109.74.201.62:8080";
    String TAG = "com.gradians.collect";
    String TAG_ID = "com.gradians.collect.ID";
    String TOKEN_KEY = "token";
    String NAME_KEY = "name";
    String EMAIL_KEY = "email";
    
    String ITEMS_KEY = "ws";
    String QUIZ_ID_KEY = "quizId";
    String QUIZ_NAME_KEY = "quiz";
    String QUIZ_PATH_KEY = "locn";
    String STATE_KEY = "state";
    String ID_KEY = "id";
    String GR_ID_KEY = "grId";
    String GR_PATH_KEY = "img";
    String SCAN_KEY = "scan";
    String MARKS_KEY = "marks";
    
    String QUESTIONS_DIR_NAME = "questions";
    String ANSWERS_DIR_NAME = "answers";
    String SOLUTIONS_DIR_NAME = "solutions";
    String MANIFEST_DIR_NAME = "manifests";
    String FEEDBACK_DIR_NAME = "feedback";
    String UPLOAD_DIR_NAME = "upload";
    
    short WAITING = 0,
          DOWNLOADED = 1,
          CAPTURED = 2,
          SENT = 3,
          RECEIVED = 4,
          GRADED = 5,
          UNMARKED = 6,
          MARKED = 7, 
          SAVED = 8;
    
}
