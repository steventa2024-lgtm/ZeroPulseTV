// Android can resolve RECOGNIZE_SPEECH to a chooser before a real service is selected.
export function activityList(output) {
  return output.split(/\r?\n/).map(line => line.trim()).filter(line => /^[\w.]+\/[\w.$]+$/.test(line));
}
export function recognitionWindow(component, candidates) {
  if (!component) return 'none';
  const [pkg, activity] = component.split('/');
  if (pkg === 'android' || /(?:^|\.)intentresolver$/.test(pkg) || /(?:Chooser|Resolver)Activity$/.test(activity)) return 'chooser';
  if (candidates.some(name => name.split('/')[0] === pkg)) return 'recognizer';
  return 'other';
}
