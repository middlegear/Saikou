package ani.saikou.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import ani.saikou.R
import ani.saikou.currContext
import ani.saikou.databinding.ActivityFaqBinding
import ani.saikou.initActivity

class FAQActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFaqBinding

    private val faqs = listOf(

        Triple(
            R.drawable.ic_round_help_24,
            currContext()!!.getString(R.string.question_1),
            currContext()!!.getString(R.string.answer_1)
        ),
        Triple(
            R.drawable.ic_round_auto_awesome_24,
            currContext()!!.getString(R.string.question_2),
            currContext()!!.getString(R.string.answer_2)
        ),
        Triple(
            R.drawable.ic_round_auto_awesome_24,
            currContext()!!.getString(R.string.question_17),
            currContext()!!.getString(R.string.answer_17)
        ),
        Triple(
            R.drawable.ic_round_download_24,
            currContext()!!.getString(R.string.question_3),
            currContext()!!.getString(R.string.answer_3)
        ),
        Triple(
            R.drawable.ic_round_help_24,
            currContext()!!.getString(R.string.question_16),
            currContext()!!.getString(R.string.answer_16)
        ),

        Triple(
            R.drawable.ic_anilist,
            currContext()!!.getString(R.string.question_6),
            currContext()!!.getString(R.string.answer_6)
        ),

        Triple(
            R.drawable.ic_round_menu_book_24,
            currContext()!!.getString(R.string.question_8),
            currContext()!!.getString(R.string.answer_8)
        ),
        Triple(
            R.drawable.ic_round_lock_open_24,
            currContext()!!.getString(R.string.question_9),
            currContext()!!.getString(R.string.answer_9)
        ),
        Triple(
            R.drawable.ic_round_smart_button_24,
            currContext()!!.getString(R.string.question_10),
            currContext()!!.getString(R.string.answer_10)
        ),
        Triple(
            R.drawable.ic_round_smart_button_24,
            currContext()!!.getString(R.string.question_11),
            currContext()!!.getString(R.string.answer_11)
        ),
        Triple(
            R.drawable.ic_round_info_24,
            currContext()!!.getString(R.string.question_12),
            currContext()!!.getString(R.string.answer_12)
        ),
        Triple(
            R.drawable.ic_round_help_24,
            currContext()!!.getString(R.string.question_13),
            currContext()!!.getString(R.string.answer_13)
        ),


    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaqBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)

        binding.devsTitle2.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.devsRecyclerView.adapter = FAQAdapter(faqs, supportFragmentManager)
        binding.devsRecyclerView.layoutManager = LinearLayoutManager(this)
    }
}
