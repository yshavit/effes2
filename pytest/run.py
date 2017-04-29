#!/usr/bin/python
import glob
import os
import re
import shutil
import subprocess
from subprocess import PIPE
import sys
import tempfile
import time
import yaml

DEBUG_KEY = 'EF_DEBUG'
# All paths are relative to here
TESTS_DIR = 'tests'
EXPECTEDS_DIR = 'results/expected'
ACTUALS_DIR = 'results/actual'
EF_CLASSPATH = '../efct'
EF_JAR = 'effesvm.jar'


def abs_path(relative):
    home_dir = os.path.abspath(os.path.dirname(__file__))
    return '%s/%s' % (home_dir, relative)


def find_results(desc_filter=None, tests_dir=TESTS_DIR):
    '''Result is a map where the key is a file path, and the value is
    a list of tuples: (description, expected, actual)'''
    tests_dir_abs = abs_path(tests_dir)
    results = {}

    prefix_len = len(tests_dir_abs) + 1  # +1 for the trailing slash
    for test_p in sorted(glob.glob('%s**/*.yaml' % tests_dir_abs)):
        test_name = test_p[prefix_len:]
        test_name = os.path.splitext(test_name)[0]
        print '\x1b[4m%s:\x1b[0m' % test_name
        with open(test_p) as test_f:
            case_results = []
            for case_no, case in enumerate(yaml.load_all(test_f)):
                stdin = case.get('stdin', '')
                stdout = case.get('stdout', '').strip()
                desc = case.get('desc', 'case_%d' % case_no)
                skipped = False
                if desc_filter:
                    full_desc = '%s:%s' % (test_name, desc)
                    skipped = not desc_filter(full_desc)
                    if skipped:
                        sys.stdout.write('\x1b[2m')  # dim
                if skipped:
                    print ' \x1b[2m%s (skipped)\x1b[0m' % desc  # dim
                else:
                    sys.stdout.write(' %s:' % desc)
                    sys.stdout.flush()
                    entry_module = case['entry_module']
                    args = case.get('args', [])
                    in_files = case.get('initial_files', {})
                    actual, actual_err, actual_files = run_effes(entry_module, stdin, args, in_files)
                    expected_files = case.get('result_files', {})
                    expected_files = fill_in_file_shorthand(expected_files, in_files)
                    if stdout == actual and expected_files == actual_files:
                        print ' OK%s' % (', but with stderr' if actual_err else '')
                    else:
                        print ' \x1b[1mFAIL\x1b[0m'  # bold
                    case_results.append((desc, stdout, actual, actual_err,
                                         expected_files, actual_files))
            if case_results:
                results[test_name] = case_results
    return results


def run_effes(entry_module, stdin, args, in_files):
    debug_option = os.environ.get(DEBUG_KEY, '')
    proc_args = ['java', '-Ddebug=%s' % debug_option, '-jar', abs_path(EF_JAR), entry_module]
    proc_args.extend(args)
    classpath = abs_path(EF_CLASSPATH)
    proc_env = dict(os.environ)
    proc_env['EFFES_CLASSPATH']= classpath
    working_dir = tempfile.mkdtemp()
    create_files(working_dir, in_files)
    p = subprocess.Popen(proc_args, stderr=PIPE, stdin=PIPE, stdout=PIPE, env=proc_env, cwd=working_dir)
    if debug_option.lower() == '0:suspend':
        sys.stderr.write(' (pid %s' % p.pid)
        sys.stderr.flush()
        time.sleep(0.5)
        port_p = subprocess.Popen(['netstat', '-nlp'], stderr=PIPE, stdin=PIPE, stdout=PIPE)
        port_stdout, port_stderr = port_p.communicate()
        saw_a_port = False
        for port_line in port_stdout.split('\n'):
            if re.search('%s/java\\s*$' % p.pid, port_line):
                m = re.search(':(\\d+)', port_line)
                if m:
                    if not saw_a_port:
                        sys.stderr.write(' port ')
                        saw_a_port = True
                    sys.stderr.write(m.group(1))
        sys.stderr.write(')')
        sys.stderr.flush()
    stdout, stderr = p.communicate(stdin)
    out_files = dir_to_object(working_dir)
    shutil.rmtree(working_dir)
    return stdout.strip(), stderr.strip(), out_files


def create_files(cwd, files):
    for name, contents in files.iteritems():
        child_path = '%s/%s' % (cwd, name)
        if type(contents) is dict:
            os.mkdir(child_path)
            create_files(child_path, contents)
        elif type(contents) is str:
            with open(child_path, 'w') as fh:
                fh.write(contents)
        else:
            raise Exception('unknown type for %s: %s (%s)' % (
                name, type(contents), contents))


def dir_to_object(cwd):
    obj = {}
    for name in os.listdir(cwd):
        path = '%s/%s' % (cwd, name)
        if os.path.isdir(path):
            obj[name] = dir_to_object(path)
        else:
            with open(path) as fh:
                obj[name] = fh.read()
    return obj


def fill_in_file_shorthand(shorthand, template):
    '''for each (k, v) in shorthand, if the value is ['...'], then use
    the value from template instead.'''
    for k, v in shorthand.iteritems():
        if v == ['...']:
            if k in template:
                shorthand[k] = template[k]
            else:
                del shorthand[k]
        elif type(v) is not str:
            raise Exception('no recursion yet')
    return shorthand

     
def clear_dir(dir_p):
    dir_p = abs_path(dir_p)
    if os.path.exists(dir_p):
        shutil.rmtree(dir_p)
    os.makedirs(dir_p)
    return dir_p


def results_to_files(results):
    def line_break(fh, header):
        fh.write('%s\n%s:\n' % ('-' * 20, header))

    for test_p, case_results in results.iteritems():
        expected_d = clear_dir('%s/%s' % (EXPECTEDS_DIR, test_p))
        actual_d = clear_dir('%s/%s' % (ACTUALS_DIR, test_p))
        for r in case_results:
            (
             desc,
             expected,
             actual,
             actual_err, 
             expected_files, 
             actual_files
            ) = r
            matched_stdout = (expected == actual)
            matched_files = (expected_files == actual_files)
            if matched_stdout and matched_files and (not actual_err):
                continue
            desc = re.sub('\\W', '_', desc)
            with open('%s/%s' % (expected_d, desc), 'w') as expect_f, \
                 open('%s/%s' % (actual_d, desc), 'w') as actual_f:
                if not matched_stdout:
                    expect_f.write(expected)
                    actual_f.write(actual)
                if not matched_files:
                    line_break(expect_f, 'files')
                    expect_f.write(yaml.dump(expected_files, default_style='|'))
                    line_break(actual_f, 'files')
                    actual_f.write(yaml.dump(actual_files, default_style='|'))
                if actual_err:
                    line_break(actual_f, 'stderr')
                    actual_f.write(actual_err)


if __name__ == '__main__':
    ef_jar = abs_path(EF_JAR)
    if not os.path.exists(ef_jar):
        sys.stderr.write('No such file: %s\n' % ef_jar)
        exit(50)
    tests_filter = None
    if sys.argv[1:]:
        def tests_filter(test_desc):
            for pattern in sys.argv[1:]:
                if re.search(pattern, test_desc):
                    return True
            return False
    results = find_results(tests_filter)
    results_to_files(results)
    # results is a map of test -> [(desc, expected, actual, actual_err)]
    errs = []
    for file_res in results.itervalues():
        errs.extend(['e' for e in file_res if e[1] != e[2] or e[4] != e[5]])

    case_errors = reduce(lambda a, b: a + b, map(len, errs), 0)
    if case_errors:
        test_errors = len(filter(bool, errs))
        print
        print '%d error%s in %d test file%s.' % (
                case_errors,
                '' if case_errors == 1 else 's',
                test_errors,
                '' if test_errors == 1 else 's')
        exit_code = 1
    else:
        print 'No errors!'
        exit_code = 0
    exit(exit_code)
