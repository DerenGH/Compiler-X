package com.example.compilerx

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class ResultFragment : Fragment() {

    companion object {
        fun newInstance(userAnswers: List<UserAnswer>): ResultFragment {
            val fragment = ResultFragment()
            val args = Bundle()
            args.putSerializable("userAnswers", ArrayList(userAnswers))
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_quiz_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val answers = arguments?.getSerializable("userAnswers") as? ArrayList<UserAnswer> ?: arrayListOf()
        val score = answers.count { it.isCorrect }
        val total = answers.size
        val percent = if (total > 0) (score * 100) / total else 0

        view.findViewById<TextView>(R.id.tvPercentage).text = "$percent%"
        view.findViewById<TextView>(R.id.tvScoreText).text = "You scored $score out of $total"
        view.findViewById<ProgressBar>(R.id.progressCircle).progress = percent

        // 1. Play Again - Returns to Setup Screen
        view.findViewById<Button>(R.id.btnPlayAgain).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, QuizFragment())
                .commit()
        }

        // 2. Home
        view.findViewById<Button>(R.id.btnHome).setOnClickListener {
            parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        // 3. Review
        view.findViewById<Button>(R.id.btnReview).setOnClickListener {
            val reviewFrag = ReviewFragment.newInstance(answers)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, reviewFrag)
                .addToBackStack(null)
                .commit()
        }
    }
}