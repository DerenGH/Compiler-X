package com.example.compilerx

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import java.io.Serializable

class PlayQuizFragment : Fragment() {

    private var questions: List<Question> = listOf()
    private var userAnswers = mutableListOf<UserAnswer>()
    private var currentQuestionIdx = 0
    private var timer: CountDownTimer? = null
    private var isTimerEnabled = true

    companion object {
        fun newInstance(questions: List<Question>, timerEnabled: Boolean): PlayQuizFragment {
            val fragment = PlayQuizFragment()
            val args = Bundle()
            args.putSerializable("questions_list", ArrayList(questions))
            args.putBoolean("timer_enabled", timerEnabled)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_play_quiz, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        questions = arguments?.getSerializable("questions_list") as List<Question>
        isTimerEnabled = arguments?.getBoolean("timer_enabled") ?: true
        displayQuestion()
        if (isTimerEnabled) startTimer(view)
    }

    private fun displayQuestion() {
        val view = view ?: return
        if (currentQuestionIdx >= questions.size) {
            navigateToResults()
            return
        }

        val q = questions[currentQuestionIdx]
        view.findViewById<TextView>(R.id.tvQuestionText).text = q.text
        val rgOptions = view.findViewById<RadioGroup>(R.id.rgOptions)
        rgOptions.clearCheck()

        val rbIds = listOf(R.id.rbOption1, R.id.rbOption2, R.id.rbOption3, R.id.rbOption4)
        for (i in 0 until 4) {
            view.findViewById<RadioButton>(rbIds[i]).text = q.options[i]
        }

        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val selectedId = rgOptions.checkedRadioButtonId
            if (selectedId == -1) {
                Toast.makeText(context, "Select an answer!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedIdx = rgOptions.indexOfChild(view.findViewById(selectedId))
            val isCorrect = selectedIdx == q.correctIdx

            // Record the answer
            userAnswers.add(UserAnswer(q, selectedIdx, isCorrect))

            currentQuestionIdx++
            displayQuestion()
        }
    }

    private fun startTimer(view: View) {
        val tvTimer = view.findViewById<TextView>(R.id.tvTimer)
        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvTimer.text = "Time: ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() { navigateToResults() }
        }.start()
    }

    private fun navigateToResults() {
        timer?.cancel()
        // If they didn't finish all questaions, mark remaining as wrong
        while (userAnswers.size < questions.size) {
            userAnswers.add(UserAnswer(questions[userAnswers.size], -1, false))
        }
        val resultFrag = ResultFragment.newInstance(userAnswers)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, resultFrag)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}