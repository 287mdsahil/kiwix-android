/*
 * Kiwix Android
 * Copyright (C) 2018  Kiwix <android.kiwix.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.kiwix.kiwixmobile.zim_manager.fileselect_view;

import static org.kiwix.kiwixmobile.utils.Constants.REQUEST_STORAGE_PERMISSION;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.kiwix.kiwixmobile.KiwixApplication;
import org.kiwix.kiwixmobile.R;
import org.kiwix.kiwixmobile.Zim;
import org.kiwix.kiwixmobile.base.BasePresenter;
import org.kiwix.kiwixmobile.database.BookDao;
import org.kiwix.kiwixmobile.library.entity.LibraryNetworkEntity;

import java.util.ArrayList;

import javax.inject.Inject;
import org.kiwix.kiwixmobile.library.entity.LibraryNetworkEntity.Book;
import org.kiwix.kiwixmobile.utils.SharedPreferenceUtil;
import org.kiwix.kiwixmobile.utils.TestingUtils;
import org.kiwix.kiwixmobile.utils.files.FileSearch;
import org.kiwix.kiwixmobile.utils.files.FileSearch.ResultListener;
import org.kiwix.kiwixmobile.utils.files.FileUtils;
import org.kiwix.kiwixmobile.zim_manager.ZimManageActivity;
import org.kiwix.kiwixmobile.zim_manager.ZimManagePresenter;
import org.kiwix.kiwixmobile.zim_manager.library_view.LibraryPresenter;

/**
 * Created by EladKeyshawn on 06/04/2017.
 */
public class ZimFileSelectPresenter extends BasePresenter<ZimFileSelectViewCallback> {

  @Inject
  BookDao bookDao;

  @Inject
  LibraryPresenter libraryPresenter;

  @Inject
  SharedPreferenceUtil sharedPreferenceUtil;

  List<Zim> localZims = new ArrayList<>();
  LocalZimAdapter localZimAdapter;


  @Inject
  public ZimFileSelectPresenter() {
  }

  @Override
  public void attachView(ZimFileSelectViewCallback mvpView, Context context) {
    super.attachView(mvpView, context);
    localZimAdapter = new LocalZimAdapter(context, 0, localZims);
  }

  public void loadLocalZimFiles() {
    showZims(bookDao.getZims());
  }

  public void showZims(List<Zim> newZims) {
    Collections.sort(newZims, new ZimComparator());
    localZims.clear();
    localZims.addAll(newZims);
    getMvpView().setListViewAdapter(localZimAdapter);
    localZimAdapter.notifyDataSetChanged();
    checkEmpty();
    checkPermissions();
  }

  public boolean deleteSpecificZimFile(int position) {
    Zim zim = localZims.get(position);
    zim.delete();
    bookDao.deleteBook(zim.getId());
    localZims.remove(position);
    localZimAdapter.notifyDataSetChanged();
    checkEmpty();
    libraryPresenter.refreshLibrary();
    return true;
  }

  public void checkEmpty() {
    if (localZims.size() == 0){
      getMvpView().showNoFilesMessage();
    } else {
      getMvpView().hideNoFilesMessage();
    }
  }

  // Add book after download
  public void addZim(String path) {
    Zim zim = FileSearch.fileToZim(path);
    if (zim != null) {
      localZims.add(zim);
      localZimAdapter.notifyDataSetChanged();
      //bookDao.saveZims(localZims);
      checkEmpty();
    }
  }

  public void checkPermissions(){
    if (ContextCompat.checkSelfPermission(getContext(),
        Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT > 18) {
      Toast.makeText(getContext(), getContext().getResources().getString(R.string.request_storage), Toast.LENGTH_LONG)
          .show();
      getMvpView().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
          REQUEST_STORAGE_PERMISSION);
    } else {
      getFiles();
    }
  }



  public void getFiles() {
    TestingUtils.bindResource(ZimFileSelectFragment.class);
    getMvpView().setRefreshing(true);
    getMvpView().setListViewAdapter(localZimAdapter);
    checkEmpty();

    new FileSearch(getContext(), new ResultListener() {
      @Override
      public void onZimFound(Zim zim) {
        if (!localZims.contains(zim)) {
          getMvpView().runOnUiThread(() -> {
            Log.i("Scanner", "File Search: Found Book " + zim.getTitle());
            localZims.add(zim);
            localZimAdapter.notifyDataSetChanged();
            checkEmpty();
          });
        }
      }

      @Override
      public void onScanCompleted() {
        // Remove non-existent books
        ArrayList<Zim> zims = new ArrayList<>(localZims);
        for (Zim zim : zims) {
          if (!new File(zim.getFilePath()).exists() || !new File(zim.getFilePath()).canRead()) {
            localZims.remove(zim);
          }
        }

        boolean cached = localZims.containsAll(bookDao.getZims()) && bookDao.getZims().containsAll(localZims);

        // If content changed then update the list of downloadable books
        libraryPresenter.refreshLibrary();

        // Save the current list of books
        getMvpView().runOnUiThread(() -> {
          localZimAdapter.notifyDataSetChanged();
          //bookDao.saveBooks(mFiles);
          checkEmpty();
          TestingUtils.unbindResource(ZimFileSelectFragment.class);

          // Stop swipe refresh animation
          getMvpView().setRefreshing(false);
        });
      }
    }).scan(sharedPreferenceUtil.getPrefStorage());
  }

  public void onRequestPermissionsResult(int requestCode,
      String permissions[], int[] grantResults) {
    switch (requestCode) {
      case REQUEST_STORAGE_PERMISSION: {
        if (grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          getFiles();
        } else if (grantResults.length != 0) {
          getMvpView().finishActivity();
        }
      }

    }
  }

  private class ZimComparator implements Comparator<Zim> {
    @Override
    public int compare(Zim zim1, Zim zim2) {
      return zim1.getTitle().compareTo(zim2.getTitle());
    }
  }
}
