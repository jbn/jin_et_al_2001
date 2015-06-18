from setuptools import setup, find_packages
from codecs import open
from os import path

here = path.abspath(path.dirname(__file__))

with open(path.join(here, 'README.rst'), encoding='utf-8') as f:
    long_description = f.read()

setup(
    name='jingenr',
    version='0.0.1',
    description='Replication of Model II from Jin et al (2001)',
    long_description=long_description,
    url='https://github.com/jbn/jin_et_al_2001',
    author='John Bjorn Nelson',
    author_email='jbn@pathdependent.com',
    license='MIT',

    # See https://pypi.python.org/pypi?%3Aaction=list_classifiers
    classifiers=[
        'Development Status :: 3 - Alpha',
        'Intended Audience :: Science/Research',
        'Topic :: Sociology',
        'License :: OSI Approved :: MIT License',
        'Programming Language :: Python :: 2.7',
        'Programming Language :: Python :: 3',
        'Programming Language :: Python :: 3.2',
        'Programming Language :: Python :: 3.3',
        'Programming Language :: Python :: 3.4',
    ],
    keywords='sna sociology sociogenesis',
    packages=find_packages(exclude=['contrib', 'docs', 'tests*'])
)