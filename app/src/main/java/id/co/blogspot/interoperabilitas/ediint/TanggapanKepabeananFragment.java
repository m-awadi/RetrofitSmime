package id.co.blogspot.interoperabilitas.ediint;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/**
 * Created by dawud_tan on 10/4/17.
 */

public class TanggapanKepabeananFragment extends DialogFragment {
    private TextView balasanServer;
    private Button sembunyikan;

    public TanggapanKepabeananFragment() {
    }

    public static TanggapanKepabeananFragment newInstance(String tanggapanServer) {
        TanggapanKepabeananFragment frag = new TanggapanKepabeananFragment();
        Bundle args = new Bundle();
        args.putString("tanggapanServer", tanggapanServer);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tanggapan_kepabeanan_fragment, container);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        balasanServer = view.findViewById(R.id.balasanServer);
        getDialog().setTitle("Tanggapan Server");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            balasanServer.setText(Html.fromHtml(getArguments().getString("tanggapanServer"), Html.FROM_HTML_MODE_COMPACT));
        } else {
            balasanServer.setText(Html.fromHtml(getArguments().getString("tanggapanServer")));
        }

        balasanServer.requestFocus();
        sembunyikan = view.findViewById(R.id.sembunyikan);
        sembunyikan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TanggapanKepabeananFragment.this.dismiss();
            }
        });
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
}