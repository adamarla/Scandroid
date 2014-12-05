package com.gradians.collect;

import java.io.File;
import java.util.Iterator;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class DoubtManifest extends BaseManifest {

    public DoubtManifest(File dir, Topic[] topics) throws Exception {
        super(dir, topics);
    }

    @Override
    public Quij[] all() {
        Quij quiz = new Quij(null, null, 0, 0, null, null);
        Set<String> questionIds = questionByIdMap.keySet();
        Iterator<String> iterator = questionIds.iterator();
        while (iterator.hasNext()) {
            quiz.add(questionByIdMap.get(iterator.next()));
        }        
        return new Quij[] { quiz };
    }

    @Override
    public void parse(JSONArray items, boolean remote) throws Exception {
        JSONObject item;
        for (int i = 0; i < items.size(); i++) {
            item = (JSONObject)items.get(i);
            Question question = new Question(
                (String)item.get(NAME_KEY), (String)item.get(ID_KEY),
                0, "0", (String)item.get(IMG_PATH_KEY),(short)0);
            question.setPgMap("1");
            question.setBotSoln(true);
            if (item.get(EXAMINER_KEY) != null)
                question.setExaminer(((Long)item.get(EXAMINER_KEY)).intValue());
            if (item.get(SCAN_KEY) != null) {
                question.setScanLocn((String)item.get(SCAN_KEY));
            }
            questionByIdMap.put(question.getId(), question);            
        }
    }

}
