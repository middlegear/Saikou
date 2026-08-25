package ani.saikou.others

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ani.saikou.BottomSheetDialogFragment
import ani.saikou.databinding.BottomSheetCustomBinding

open class CustomBottomDialog : BottomSheetDialogFragment() {
    private var _binding: BottomSheetCustomBinding? = null
    private val binding get() = _binding!!

    private val viewList = mutableListOf<View>()
    fun addView(view: View) {
        viewList.add(view)
    }

    var title: String? = null
    fun setTitleText(string: String) {
        title = string
    }

    private var checkText: String? = null
    private var checkChecked: Boolean = false
    private var checkCallback: ((Boolean) -> Unit)? = null

    fun setCheck(text: String, checked: Boolean, callback: ((Boolean) -> Unit)) {
        checkText = text
        checkChecked = checked
        checkCallback = callback
    }

    private var negativeText: String? = null
    private var negativeCallback: (() -> Unit)? = null
    fun setNegativeButton(text: String, callback: (() -> Unit)) {
        negativeText = text
        negativeCallback = callback
    }

    private var positiveText: String? = null
    private var positiveCallback: (() -> Unit)? = null
    fun setPositiveButton(text: String, callback: (() -> Unit)) {
        positiveText = text
        positiveCallback = callback
    }

    private var dismissCallback: (() -> Unit)? = null
    fun setOnDismissListener(callback: () -> Unit) {
        dismissCallback = callback
    }

    private var dismissedByButton = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCustomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.bottomSheerCustomTitle.text = title

        viewList.forEach {
            binding.bottomDialogCustomContainer.addView(it)
        }

        if (checkText != null) binding.bottomDialogCustomCheckBox.apply {
            visibility = View.VISIBLE
            text = checkText
            isChecked = checkChecked
            setOnCheckedChangeListener { _, checked ->
                checkCallback?.invoke(checked)
            }
        }

        if (negativeText != null) binding.bottomDialogCustomNegative.apply {
            visibility = View.VISIBLE
            text = negativeText
            setOnClickListener {
                dismissedByButton = true
                negativeCallback?.invoke()
                dismiss()
            }
        }

        if (positiveText != null) binding.bottomDialogCustomPositive.apply {
            visibility = View.VISIBLE
            text = positiveText
            setOnClickListener {
                dismissedByButton = true
                positiveCallback?.invoke()
                dismiss()
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!dismissedByButton) {
            dismissCallback?.invoke()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = CustomBottomDialog()
    }
}