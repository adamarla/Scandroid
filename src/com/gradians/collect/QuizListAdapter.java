package com.gradians.collect;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class QuizListAdapter extends BaseAdapter implements IConstants {
    
    public QuizListAdapter(Context context, Quij[] quizzes) {
        this.inflater = LayoutInflater.from(context);
        this.quizzes = quizzes;
        this.dirtys = new ArrayList<Question>();
    }
        
    public void update(int quizPosn, Question[] questions) {
        Quij quiz = quizzes[quizPosn];
        Question q = null;
        for (int i = 0; i < questions.length; i++) {
            q = quiz.get(i);
            if (questions[i].getState() != q.getState() ||
                !questions[i].getPgMap("").equals(q.getPgMap(""))) {
                q.setPgMap(questions[i].getPgMap());
                q.setState(questions[i].getState());
                dirtys.add(q);
            }
        }
        quiz.determineState();
        notifyDataSetChanged();
    }
    
    public Question[] getDirtys() {
        return dirtys.toArray(new Question[dirtys.size()]);
    }

    @Override
    public int getCount() {
        return quizzes.length;
    }

    @Override
    public Object getItem(int position) {
        return quizzes[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.layout_quiz, parent, false);
        }        
        
        TextView tv = (TextView)convertView.findViewById(R.id.tvQuiz);
        tv.setTag(position);
        
        Quij quiz = (Quij)getItem(position);
        tv.setText(quiz.toString());
        
        TextView tvTotal = (TextView)convertView.findViewById(R.id.tvTotal);
        tvTotal.setText(quiz.getDisplayTotal());
        
        return convertView;
    }

    private ArrayList<Question> dirtys;
    private Quij[] quizzes;
    private LayoutInflater inflater;

}
