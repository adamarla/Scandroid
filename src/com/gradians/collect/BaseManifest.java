package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Properties;

import org.json.simple.JSONArray;

public abstract class BaseManifest implements IConstants {

    public BaseManifest(File dir, JSONArray items, Topic[] topics) 
        throws Exception {
        this.topics = topics;
        File stateFile = new File(dir, STATE_FILE);
        stateFile.createNewFile();
        state = new Properties();
        state.load(new FileInputStream(stateFile));
        questionByIdMap = new HashMap<String, Question>();
        parse(items);
    }

    public void update(Question[] questions) {
        Question question;
        for (Question q : questions) {
            question = questionByIdMap.get(q.getId());
            question.setState(q.getState());
            question.setPgMap(q.getPgMap());
        }
    }
    
    public Question[] getAllItems() {
        int size = questionByIdMap.size();
        return questionByIdMap.values().toArray(new Question[size]);
    }
    
    public String toJSONArray() {
        JSONArray array = new JSONArray();
        Question[] questions = new Question[questionByIdMap.size()];
        questionByIdMap.values().toArray(questions);
        for (Question q : questions) {
            array.add(q);
        }
        return array.toJSONString();
    }
    
    public abstract void parse(JSONArray items) throws Exception;

    protected Topic[] topics;
    protected Properties state;
    protected HashMap<String, Question> questionByIdMap;

}