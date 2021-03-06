/*
 *   ownCloud Android client application
 *
 *   @author David A. Velasco
 *   Copyright (C) 2015  ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.owncloud.android.ui.preview;

import android.accounts.Account;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.SparseArray;
import android.view.ViewGroup;

import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.datamodel.VirtualFolderType;
import com.owncloud.android.ui.fragment.FileFragment;
import com.owncloud.android.utils.FileSortOrderByName;
import com.owncloud.android.utils.FileStorageUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import javax.annotation.Nullable;

/**
 * Adapter class that provides Fragment instances
 */
public class PreviewImagePagerAdapter extends FragmentStatePagerAdapter {
    
    private Vector<OCFile> mImageFiles;
    private Account mAccount;
    private Set<Object> mObsoleteFragments;
    private Set<Integer> mObsoletePositions;
    private Set<Integer> mDownloadErrors;
    private FileDataStorageManager mStorageManager;

    private SparseArray<FileFragment> mCachedFragments;

    /**
     * Constructor
     * 
     * @param fragmentManager   {@link FragmentManager} instance that will handle
     *                          the {@link Fragment}s provided by the adapter.
     * @param parentFolder      Folder where images will be searched for.
     * @param storageManager    Bridge to database.
     */
    public PreviewImagePagerAdapter(FragmentManager fragmentManager, OCFile parentFolder,
                                    Account account, FileDataStorageManager storageManager,
                                    boolean onlyOnDevice) {
        super(fragmentManager);
        
        if (fragmentManager == null) {
            throw new IllegalArgumentException("NULL FragmentManager instance");
        }
        if (parentFolder == null) {
            throw new IllegalArgumentException("NULL parent folder");
        } 
        if (storageManager == null) {
            throw new IllegalArgumentException("NULL storage manager");
        }

        mAccount = account;
        mStorageManager = storageManager;
        mImageFiles = mStorageManager.getFolderImages(parentFolder, onlyOnDevice);
        
        mImageFiles = FileSortOrderByName.sort_a_to_z.sortCloudFiles(mImageFiles);
        
        mObsoleteFragments = new HashSet<>();
        mObsoletePositions = new HashSet<>();
        mDownloadErrors = new HashSet<>();
        mCachedFragments = new SparseArray<>();
    }

    /**
     * Constructor
     *
     * @param fragmentManager {@link FragmentManager} instance that will handle
     *                        the {@link Fragment}s provided by the adapter.
     * @param type            Type of virtual folder, e.g. favorite or photos
     * @param storageManager  Bridge to database.
     */
    public PreviewImagePagerAdapter(FragmentManager fragmentManager, VirtualFolderType type,
                                    Account account, FileDataStorageManager storageManager) {
        super(fragmentManager);

        if (fragmentManager == null) {
            throw new IllegalArgumentException("NULL FragmentManager instance");
        }
        if (type == null) {
            throw new IllegalArgumentException("NULL parent folder");
        }
        if (storageManager == null) {
            throw new IllegalArgumentException("NULL storage manager");
        }

        mAccount = account;
        mStorageManager = storageManager;
        mImageFiles = mStorageManager.getVirtualFolderContent(type, true);

        if (type == VirtualFolderType.PHOTOS) {
            mImageFiles = FileStorageUtils.sortOcFolderDescDateModified(mImageFiles);
        }

        mObsoleteFragments = new HashSet<>();
        mObsoletePositions = new HashSet<>();
        mDownloadErrors = new HashSet<>();
        mCachedFragments = new SparseArray<>();
    }

    /**
     * Returns the image files handled by the adapter.
     *
     * @return OCFile desired image or null if position is not in adapter
     */
    @Nullable
    public OCFile getFileAt(int position) {
        try {
            return mImageFiles.get(position);
        } catch (IndexOutOfBoundsException exception) {
            return null;
        }
    }

    
    public Fragment getItem(int i) {
        OCFile file = mImageFiles.get(i);
        Fragment fragment;

        if (file.isDown()) {
            fragment = PreviewImageFragment.newInstance(file, mObsoletePositions.contains(i), false);
        } else {
            if (mDownloadErrors.contains(i)) {
                fragment = FileDownloadFragment.newInstance(file, mAccount, true);
                ((FileDownloadFragment) fragment).setError(true);
                mDownloadErrors.remove(i);
            } else {
                if (file.isEncrypted()) {
                    fragment = FileDownloadFragment.newInstance(file, mAccount, mObsoletePositions.contains(i));
                } else {
                    fragment = PreviewImageFragment.newInstance(file, mObsoletePositions.contains(i), true);
                }
            }
        }

        mObsoletePositions.remove(i);
        return fragment;
    }

    public int getFilePosition(OCFile file) {
        return mImageFiles.indexOf(file);
    }
    
    @Override
    public int getCount() {
        return mImageFiles.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return mImageFiles.get(position).getFileName();
    }

    
    public void updateFile(int position, OCFile file) {
        FileFragment fragmentToUpdate = mCachedFragments.get(position);
        if (fragmentToUpdate != null) {
            mObsoleteFragments.add(fragmentToUpdate);
        }
        mObsoletePositions.add(position);
        mImageFiles.set(position, file);
    }
    
    
    public void updateWithDownloadError(int position) {
        FileFragment fragmentToUpdate = mCachedFragments.get(position);
        if (fragmentToUpdate != null) {
            mObsoleteFragments.add(fragmentToUpdate);
        }
        mDownloadErrors.add(position);
    }
    
    @Override
    public int getItemPosition(Object object) {
        if (mObsoleteFragments.contains(object)) {
            mObsoleteFragments.remove(object);
            return POSITION_NONE;
        }
        return super.getItemPosition(object);
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Object fragment = super.instantiateItem(container, position);
        mCachedFragments.put(position, (FileFragment) fragment);
        return fragment;
    }
    
    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        mCachedFragments.remove(position);
       super.destroyItem(container, position, object);
    }


    public boolean pendingErrorAt(int position) {
        return mDownloadErrors.contains(position);
    }

    /**
     * Reset the image zoom to default value for each CachedFragments
     */
    public void resetZoom() {
        for (int i = 0; i < mCachedFragments.size(); i++) {
            FileFragment fileFragment = mCachedFragments.valueAt(i);
            
            if (fileFragment instanceof PreviewImageFragment) {
                ((PreviewImageFragment) fileFragment).getImageView().resetZoom();
            }
        }
    }
}
