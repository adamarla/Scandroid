package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Properties;

import org.json.simple.JSONArray;

public abstract class BaseManifest implements IConstants {

    public BaseManifest(File dir, Topic[] topics) 
        throws Exception {
        this.topics = topics;
        File filesDir = new File(dir, "files");
        filesDir.mkdir();
        File stateFile = new File(filesDir, STATE_FILE);
        stateFile.createNewFile();
        state = new Properties();
        state.load(new FileInputStream(stateFile));
        questionByIdMap = new HashMap<String, Question>();
    }

    public void update(Question[] questions) {
        for (Question q : questions) {            
            questionByIdMap.put(q.getId(), q);
        }
    }
    
    public Question[] getAllItems() {
        int size = questionByIdMap.size();
        return questionByIdMap.values().toArray(new Question[size]);
    }
    
    public abstract Quij[] all();
    
    public String toJSONArray() {
        JSONArray array = new JSONArray();
        Question[] questions = new Question[questionByIdMap.size()];
        questionByIdMap.values().toArray(questions);
        for (Question q : questions) {
            array.add(q);
        }
        return array.toJSONString();
    }
    
    public abstract void parse(JSONArray items, boolean remote) throws Exception;

    protected Topic[] topics;
    protected Properties state;
    protected HashMap<String, Question> questionByIdMap;

}