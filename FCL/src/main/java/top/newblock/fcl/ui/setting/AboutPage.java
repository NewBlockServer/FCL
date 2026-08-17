package top.newblock.fcl.ui.setting;

import android.content.Context;
import android.view.View;

import top.newblock.fcl.R;
import top.newblock.fcl.util.AndroidUtils;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fcllibrary.component.ui.FCLCommonPage;
import com.tungsten.fcllibrary.component.view.FCLLinearLayout;
import com.tungsten.fcllibrary.component.view.FCLUILayout;

public class AboutPage extends FCLCommonPage implements View.OnClickListener {

    private FCLLinearLayout official;
    private FCLLinearLayout community;

    public AboutPage(Context context, int id, FCLUILayout parent, int resId) {
        super(context, id, parent, resId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        official = findViewById(R.id.official);
        community = findViewById(R.id.community);
        official.setOnClickListener(this);
        community.setOnClickListener(this);
    }

    @Override
    public Task<?> refresh(Object... param) {
        return null;
    }

    @Override
    public void onClick(View v) {
        if (v == official) {
            AndroidUtils.openLink(getContext(), "https://example.com/");
        }
        if (v == community) {
            AndroidUtils.openLink(getContext(), "https://example.com/");
        }
    }
}
