package id.co.blogspot.interoperabilitas.ediint.utility;

import android.support.annotation.Nullable;

import com.nononsenseapps.filepicker.AbstractFilePickerActivity;
import com.nononsenseapps.filepicker.AbstractFilePickerFragment;

import java.io.File;

/**
 * Created by dawud_tan on 11/11/16.
 */
public class MyPickerActivity extends AbstractFilePickerActivity<File> {

    public MyPickerActivity() {
        super();
    }

    @Override
    protected AbstractFilePickerFragment<File> getFragment(@Nullable String startPath, int mode, boolean allowMultiple, boolean allowCreateDir, boolean allowExistingFile, boolean singleClick) {
        FilteredFilePickerFragment fragment = new FilteredFilePickerFragment();
        fragment.setArgs(startPath, mode, allowMultiple, allowCreateDir, allowExistingFile, singleClick);
        return fragment;
    }

}