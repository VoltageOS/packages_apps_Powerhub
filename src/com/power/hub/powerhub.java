/*
 * Copyright (C) 2026 VoltageOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.power.hub;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.core.InstrumentedFragment;
import com.power.hub.fragments.LockScreenSettings;
import com.power.hub.fragments.MiscSettings;
import com.power.hub.fragments.MonetSettings;
import com.power.hub.fragments.StatusBarSettings;

public class powerhub extends InstrumentedFragment {

  private ViewPager viewPager;
  private int currentTabIndex = 0;

  private FrameLayout[] tabs;
  private ImageView[] icons;
  private ImageView activeGlow;
  private String[] titles;
  private View globalBottomNav;

  private boolean isNavHidden = false;
  private int accumulatedScroll = 0;
  private static final int SCROLL_THRESHOLD = 10;

  private final PathInterpolator cubicBezier = new PathInterpolator(0.2f, 0.8f, 0.2f, 1.0f);

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {

    View view = inflater.inflate(R.layout.powerhub_main, container, false);

    tabs =
        new FrameLayout[] {
          view.findViewById(R.id.tab_status),
          view.findViewById(R.id.tab_lock),
          view.findViewById(R.id.tab_themes),
          view.findViewById(R.id.tab_advanced)
        };

    icons =
        new ImageView[] {
          view.findViewById(R.id.icon_status),
          view.findViewById(R.id.icon_lock),
          view.findViewById(R.id.icon_themes),
          view.findViewById(R.id.icon_advanced)
        };

    activeGlow = view.findViewById(R.id.active_glow);

    titles =
        new String[] {
          getString(R.string.statusbar_title),
          getString(R.string.lockscreen_title),
          getString(R.string.theme_title),
          "Advanced"
        };

    final View bottomNav = view.findViewById(R.id.bottom_nav);
    if (bottomNav != null && getActivity() != null) {

      ((ViewGroup) bottomNav.getParent()).removeView(bottomNav);

      int height =
          (int)
              TypedValue.applyDimension(
                  TypedValue.COMPLEX_UNIT_DIP, 72, getResources().getDisplayMetrics());
      int marginSide =
          (int)
              TypedValue.applyDimension(
                  TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());

      FrameLayout.LayoutParams params =
          new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height);
      params.gravity = android.view.Gravity.BOTTOM;
      params.leftMargin = marginSide;
      params.rightMargin = marginSide;

      params.bottomMargin = (int) TypedValue.applyDimension(
              TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());

      int colorSurface = 0;
      TypedValue tv = new TypedValue();
      if (getContext() != null
          && getContext()
              .getTheme()
              .resolveAttribute(android.R.attr.colorBackgroundFloating, tv, true)) {
        colorSurface = tv.data;
        int glassColor = (colorSurface & 0x00FFFFFF) | 0xE6000000;
        try {
          android.graphics.drawable.LayerDrawable bg =
              (android.graphics.drawable.LayerDrawable) bottomNav.getBackground();
          android.graphics.drawable.GradientDrawable baseLayer =
              (android.graphics.drawable.GradientDrawable) bg.getDrawable(0);
          baseLayer.setColor(glassColor);
        } catch (Exception e) {
        }
      }

      try {
        java.lang.reflect.Method method =
            View.class.getMethod("setBackgroundBlurRadius", int.class);
        method.invoke(bottomNav, 80);
      } catch (Exception e) {
      }

      ViewGroup root = getActivity().findViewById(android.R.id.content);
      if (root != null) {
        root.addView(bottomNav, params);
        globalBottomNav = bottomNav;

        bottomNav.post(() -> {
            android.view.WindowInsets insets = bottomNav.getRootWindowInsets();
            if (insets != null) {
                int navHeight = insets.getSystemWindowInsetBottom();
                float density = getResources().getDisplayMetrics().density;

                int finalMargin;
                if (navHeight == 0) {
                    finalMargin = (int) (16 * density);
                } else if (navHeight > 40 * density) {
                    finalMargin = (int) (12 * density);
                } else {
                    int desired = (int) (12 * density);
                    finalMargin = Math.max(desired - navHeight, 0);
                }

                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) bottomNav.getLayoutParams();
                p.bottomMargin = finalMargin;
                bottomNav.setLayoutParams(p);
            }
        });

      }
    }

    viewPager = view.findViewById(R.id.view_pager);
    viewPager.setAdapter(new PowerHubPagerAdapter(getChildFragmentManager()));
    viewPager.setOffscreenPageLimit(3);
    viewPager.addOnPageChangeListener(
        new ViewPager.OnPageChangeListener() {
          @Override
          public void onPageScrolled(
              int position, float positionOffset, int positionOffsetPixels) {}

          @Override
          public void onPageSelected(int position) {
            currentTabIndex = position;
            viewPager.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            updateTabUI(position);
          }

          @Override
          public void onPageScrollStateChanged(int state) {}
        });

    setupTabs();

    if (savedInstanceState != null) {
      currentTabIndex = savedInstanceState.getInt("current_tab_index", 0);
    }
    viewPager.setCurrentItem(currentTabIndex, false);
    updateTabUIInitial(currentTabIndex);

    return view;
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    if (globalBottomNav != null && getActivity() != null) {
      ViewGroup root = getActivity().findViewById(android.R.id.content);
      if (root != null) {
        root.removeView(globalBottomNav);
      }
      globalBottomNav = null;
    }
  }

  private void setupTabs() {
    for (int i = 0; i < tabs.length; i++) {
      final int index = i;
      tabs[i].setSoundEffectsEnabled(false);
      tabs[i].setOnClickListener(
          v -> {
            if (viewPager.getCurrentItem() != index) {
              viewPager.setCurrentItem(index, true);
            }
          });
    }
  }

  private void updateTabUI(int index) {
    int colorAccent = 0;
    int colorInactive = 0;

    if (getContext() != null) {
      TypedValue typedValue = new TypedValue();
      if (getContext().getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
        colorAccent = typedValue.data;
      }
      if (getContext()
          .getTheme()
          .resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
        colorInactive = typedValue.data;
      }
    }

    if (colorAccent != 0) {
      activeGlow.setColorFilter(colorAccent);
    }

    for (int i = 0; i < 4; i++) {
      boolean active = (i == index);

      if (active) {
        icons[i]
            .animate()
            .scaleX(1.12f)
            .scaleY(1.12f)
            .setDuration(180)
            .setInterpolator(cubicBezier)
            .start();
        if (colorAccent != 0) {
          icons[i].setColorFilter(colorAccent);
        }

        final View activeTab = tabs[i];
        activeTab.post(
            () -> {
              float tabCenter = activeTab.getX() + (activeTab.getWidth() / 2f);
              float targetX = tabCenter - (activeGlow.getWidth() / 2f);
              activeGlow
                  .animate()
                  .translationX(targetX)
                  .alpha(0.40f)
                  .setDuration(300)
                  .setInterpolator(cubicBezier)
                  .start();
            });
      } else {
        icons[i]
            .animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(180)
            .setInterpolator(cubicBezier)
            .start();

        if (colorInactive != 0) {
          icons[i].setColorFilter(colorInactive);
        } else {
          icons[i].clearColorFilter();
        }
      }
    }

    if (getActivity() != null) {
      getActivity().setTitle(titles[index]);
    }
  }

  private void updateTabUIInitial(int index) {
    int colorAccent = 0;
    int colorInactive = 0;

    if (getContext() != null) {
      TypedValue typedValue = new TypedValue();
      if (getContext().getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
        colorAccent = typedValue.data;
      }
      if (getContext()
          .getTheme()
          .resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
        colorInactive = typedValue.data;
      }
    }

    if (colorAccent != 0) {
      activeGlow.setColorFilter(colorAccent);
    }

    for (int i = 0; i < 4; i++) {
      boolean active = (i == index);

      if (active) {
        icons[i].setScaleX(1.12f);
        icons[i].setScaleY(1.12f);
        if (colorAccent != 0) {
          icons[i].setColorFilter(colorAccent);
        }

        final View activeTab = tabs[i];
        activeTab.post(
            () -> {
              float tabCenter = activeTab.getX() + (activeTab.getWidth() / 2f);
              float targetX = tabCenter - (activeGlow.getWidth() / 2f);
              activeGlow.setTranslationX(targetX);
              activeGlow.setAlpha(0.40f);
            });
      } else {
        icons[i].setScaleX(1.0f);
        icons[i].setScaleY(1.0f);
        if (colorInactive != 0) {
          icons[i].setColorFilter(colorInactive);
        } else {
          icons[i].clearColorFilter();
        }
      }
    }

    if (getActivity() != null) {
      getActivity().setTitle(titles[index]);
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt("current_tab_index", currentTabIndex);
  }

  @Override
  public int getMetricsCategory() {
    return MetricsProto.MetricsEvent.VOLTAGE;
  }

  private class PowerHubPagerAdapter extends FragmentPagerAdapter {
    public PowerHubPagerAdapter(FragmentManager fm) {
      super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
      switch (position) {
        case 0:
          return new StatusBarSettings();
        case 1:
          return new LockScreenSettings();
        case 2:
          return new MonetSettings();
        case 3:
          return new MiscSettings();
        default:
          return new StatusBarSettings();
      }
    }

    @Override
    public int getCount() {
      return 4;
    }
  }
}
