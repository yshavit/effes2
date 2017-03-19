#!/usr/bin/python
import glob
import os
import re
import shutil
import subprocess
from subprocess import PIPE
import sys
import yaml

# All paths are relative to here
TESTS_DIR = 'tests'
EXPECTEDS_DIR = 'results/expected'
ACTUALS_DIR = 'results/actual'
EF_CLASSPATH = '../efct'
EF_JAR = 'effesvm.jar'


def abs_path(relative):
    home_dir = os.path.abspath(os.path.dirname(__file__))
    return '%s/%s' % (home_dir, relative)


def find_results(tests_dir=TESTS_DIR):
    '''Result is a map where the key is a file path, and the value is
    a list of tuples: (description, expected, actual)'''
    tests_dir_abs = abs_path(tests_dir)
    results = {}
    prefix_len = len(tests_dir_abs) + 1  # +1 for the trailing slash
    for test_p in glob.glob('%s**/*.yaml' % tests_dir_abs):
        test_name = test_p[prefix_len:]
        test_name = os.path.splitext(test_name)[0]
        print '\x1b[4m%s:\x1b[0m' % test_name
        with open(test_p) as test_f:
            case_results = []
            for case_no, case in enumerate(yaml.load_all(test_f)):
                stdin = case.get('stdin', '')
                stdout = case.get('stdout', '').strip()
                desc = case.get('desc', 'case_%d' % case_no)
                entry_module = case['entry_module']
                sys.stdout.write('  %s:' % desc)
                sys.stdout.flush()
                actual = run_effes(entry_module, stdin)
                if stdout == actual:
                    print ' OK'
                else:
                    print ' \x1b[1mFAIL\x1b[0m'
                    case_results.append((desc, stdout, actual))
            results[test_name] = case_results
    return results


def run_effes(entry_module, stdin):
    proc_args = ['java', '-jar', abs_path(EF_JAR), entry_module]
    classpath = abs_path(EF_CLASSPATH)
    proc_env = {'EFFES_CLASSPATH': classpath}
    p = subprocess.Popen(proc_args, stdin=PIPE, stdout=PIPE, env=proc_env)
    stdout, ignored = p.communicate(stdin)
    out = stdout.strip()
    return out


def clear_dir(dir_p):
    dir_p = abs_path(dir_p)
    if os.path.exists(dir_p):
        shutil.rmtree(dir_p)
    os.makedirs(dir_p)
    return dir_p


def results_to_files(results):
    for test_p, case_results in results.iteritems():
        expected_d = clear_dir('%s/%s' % (EXPECTEDS_DIR, test_p))
        actual_d = clear_dir('%s/%s' % (ACTUALS_DIR, test_p))
        for desc, expected, actual in case_results:
            desc = re.sub('\\W', '_', desc)
            with open('%s/%s' % (expected_d, desc), 'w') as expect_f, \
                 open('%s/%s' % (actual_d, desc), 'w') as actual_f:
                expect_f.write(expected)
                actual_f.write(actual)


if __name__ == '__main__':
    ef_jar = abs_path(EF_JAR)
    if not os.path.exists(ef_jar):
        sys.stderr.write('No such file: %s\n' % ef_jar)
        exit(50)
    if len(sys.argv) != 1:
        sys.stderr.write("Can't pass in any arguments.")
        exit(50)
    results = find_results()
    results_to_files(results)

    case_errors = reduce(lambda a, b: a + b, map(len, results.itervalues()))
    if case_errors:
        test_errors = len(filter(bool, results.itervalues()))
        print
        print '%d error%s in %d test file%s.' % (
                case_errors,
                '' if case_errors == 1 else 's',
                test_errors,
                '' if test_errors == 1 else 's')
    else:
        print 'No errors!'

    if results:
        exit_code = 1
    else:
        exit_code = 0
    exit(exit_code)
