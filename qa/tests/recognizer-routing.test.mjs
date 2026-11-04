import assert from 'node:assert/strict';
import test from 'node:test';
import {activityList,recognitionWindow} from '../recognizer-routing.mjs';

test('Android chooser is separate from the selected speech activity', () => {
  const list = activityList('2 activities found:\n  priority=0\n com.example.voice/.Input\n com.other.voice/.Recognizer\n');
  assert.deepEqual(list,['com.example.voice/.Input','com.other.voice/.Recognizer']);
  assert.equal(recognitionWindow('android/com.android.internal.app.ResolverActivity',list),'chooser');
  assert.equal(recognitionWindow('com.android.intentresolver/.ChooserActivity',list),'chooser');
  assert.equal(recognitionWindow('com.example.voice/.ConcreteVoiceInputActivity',list),'recognizer');
  assert.equal(recognitionWindow('com.other.voice/.Recognizer',list),'recognizer');
  assert.equal(recognitionWindow('com.android.permissioncontroller/.GrantPermissionsActivity',list),'other');
  assert.equal(recognitionWindow('com.thirdparty/.Input',list),'other');
  assert.equal(recognitionWindow('',list),'none');
});
